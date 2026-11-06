package com.revenda.sales.infrastructure.adapters.out.persistence;

import com.revenda.common.jdbc.Transactions;
import com.revenda.common.jdbc.UniqueKeys;
import com.revenda.common.money.Money;
import com.revenda.common.paging.Page;
import com.revenda.common.paging.Paging;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.time.SqlTime;
import com.revenda.sales.domain.model.ClosedReason;
import com.revenda.sales.domain.model.PaymentStatus;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.PurchaseFilter;
import com.revenda.sales.domain.model.PurchaseStatus;
import com.revenda.sales.domain.model.PurchaseTransition;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.model.TransitionReason;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

/**
 * A compra na tabela {@code purchase} e a linha do tempo em {@code purchase_transition}, sempre gravadas juntas e com
 * o ator da ação; a mudança só grava sobre a versão travada.
 */
@Repository
@RequiredArgsConstructor
class JdbcPurchaseStore implements SavePurchasePort, LoadPurchasePort, LockPurchasePort {

    private static final String COLUMNS = """
            SELECT id, customer_id, customer_subject, vehicle_id, price_cents, status, payment_status, charge_id,
                   payment_code, payment_due_at, paid_at, paid_amount_cents, completed_at, closed_reason, document_key,
                   version, created_at, updated_at
              FROM purchase
            """;
    private static final String PURCHASE = COLUMNS + " WHERE id = :id";

    private static final Map<String, SalesError> UNIQUE_KEYS = Map.of(
            "purchase_one_active_per_customer", SalesError.ACTIVE_PURCHASE_EXISTS);

    private final DataSource dataSource;
    private final JdbcClient jdbc;

    @Override
    public void create(PurchaseChange requested) {
        Transactions.requireOpen(dataSource, "A compra só é gravada dentro da transação da mudança");
        Purchase purchase = requested.purchase();
        UniqueKeys.translating(UNIQUE_KEYS, () -> withState(jdbc.sql("""
                        INSERT INTO purchase (id, customer_id, customer_subject, vehicle_id, price_cents, status,
                                              payment_status, charge_id, payment_code, payment_due_at, paid_at,
                                              paid_amount_cents, completed_at, closed_reason, document_key, version,
                                              created_at, updated_at)
                        VALUES (:id, :customerId, :customerSubject, :vehicleId, :priceCents, :status, :paymentStatus,
                                :chargeId, :paymentCode, :paymentDueAt, :paidAt, :paidAmountCents, :completedAt,
                                :closedReason, :documentKey, :version, :createdAt, :updatedAt)"""), purchase)
                .param("customerId", purchase.customerId())
                .param("customerSubject", purchase.customerSubject())
                .param("vehicleId", purchase.vehicleId())
                .param("createdAt", SqlTime.utc(purchase.createdAt()))
                .update());
        record(requested.transition());
    }

    @Override
    public boolean existsActiveFor(UUID customerId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1
                                         FROM purchase
                                        WHERE customer_id = :customerId
                                          AND status IN ('PENDING', 'RESERVED', 'AWAITING_PAYMENT'))""")
                .param("customerId", customerId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void update(PurchaseChange change) {
        Transactions.requireOpen(dataSource, "A compra só é gravada dentro da transação da mudança");
        Purchase purchase = change.purchase();
        int updated = withState(jdbc.sql("""
                        UPDATE purchase
                           SET price_cents = :priceCents, status = :status, payment_status = :paymentStatus,
                               charge_id = :chargeId, payment_code = :paymentCode, payment_due_at = :paymentDueAt,
                               paid_at = :paidAt, paid_amount_cents = :paidAmountCents, completed_at = :completedAt,
                               closed_reason = :closedReason, document_key = :documentKey, version = :version,
                               updated_at = :updatedAt
                         WHERE id = :id AND version = :previousVersion"""), purchase)
                .param("previousVersion", purchase.version() - 1)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("A compra " + purchase.id() + " não está mais na versão "
                    + (purchase.version() - 1) + ": a mudança só grava sobre a linha travada");
        }
        record(change.transition());
    }

    @Override
    public Optional<Purchase> load(UUID id) {
        return jdbc.sql(PURCHASE).param("id", id).query((row, index) -> purchase(row)).optional();
    }

    @Override
    public Optional<Purchase> lock(UUID id) {
        Transactions.requireOpen(dataSource, "A compra só é travada dentro de uma transação");
        return jdbc.sql(PURCHASE + " FOR UPDATE").param("id", id).query((row, index) -> purchase(row)).optional();
    }

    @Override
    public Optional<Purchase> lockNextOverdue(Instant now) {
        Transactions.requireOpen(dataSource, "A compra só é travada dentro de uma transação");
        return jdbc.sql(COLUMNS + """
                         WHERE status = 'AWAITING_PAYMENT' AND payment_due_at < :now
                         ORDER BY payment_due_at
                         LIMIT 1
                           FOR UPDATE SKIP LOCKED""")
                .param("now", SqlTime.utc(now))
                .query((row, index) -> purchase(row))
                .optional();
    }

    @Override
    public Optional<Purchase> lockNextStalled(Instant updatedBefore) {
        Transactions.requireOpen(dataSource, "A compra só é travada dentro de uma transação");
        return jdbc.sql(COLUMNS + """
                         WHERE status IN ('PENDING', 'RESERVED') AND updated_at < :updatedBefore
                         ORDER BY updated_at
                         LIMIT 1
                           FOR UPDATE SKIP LOCKED""")
                .param("updatedBefore", SqlTime.utc(updatedBefore))
                .query((row, index) -> purchase(row))
                .optional();
    }

    @Override
    public Page<Purchase> findPage(PurchaseFilter filter, Paging paging) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        Map<String, Object> params = new HashMap<>();
        if (filter.customerSubject() != null) {
            where.append(" AND customer_subject = :customerSubject");
            params.put("customerSubject", filter.customerSubject());
        }
        if (filter.status() != null) {
            where.append(" AND status = :status");
            params.put("status", filter.status().name());
        }
        long total = jdbc.sql("SELECT count(*) FROM purchase" + where).params(params).query(Long.class).single();
        List<Purchase> items = jdbc.sql(COLUMNS + where + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", paging.size())
                .param("offset", paging.offset())
                .query((row, index) -> purchase(row))
                .list();
        return Page.of(items, paging, total);
    }

    @Override
    public List<PurchaseTransition> loadTransitions(UUID purchaseId) {
        return jdbc.sql("""
                        SELECT purchase_id, from_status, to_status, payment_status, reason, occurred_at
                          FROM purchase_transition
                         WHERE purchase_id = :purchaseId
                         ORDER BY id""")
                .param("purchaseId", purchaseId)
                .query((row, index) -> new PurchaseTransition(
                        row.getObject("purchase_id", UUID.class),
                        enumOrNull(PurchaseStatus.class, row.getString("from_status")),
                        PurchaseStatus.valueOf(row.getString("to_status")),
                        PaymentStatus.valueOf(row.getString("payment_status")),
                        enumOrNull(TransitionReason.class, row.getString("reason")),
                        SqlTime.instantOrNull(row, "occurred_at")))
                .list();
    }

    private void record(PurchaseTransition transition) {
        ActorContext actor = ActorContextHolder.get();
        if (actor.type() == ActorType.ANONYMOUS) {
            throw new IllegalStateException(
                    "A linha do tempo não aceita ator anônimo: a rota pública declara o ator antes de mudar a compra");
        }
        jdbc.sql("""
                        INSERT INTO purchase_transition (purchase_id, from_status, to_status, payment_status, reason,
                                                         actor_type, actor_id, occurred_at)
                        VALUES (:purchaseId, :fromStatus, :toStatus, :paymentStatus, :reason, :actorType, :actorId,
                                :occurredAt)""")
                .param("purchaseId", transition.purchaseId())
                .param("fromStatus", nameOrNull(transition.from()))
                .param("toStatus", transition.to().name())
                .param("paymentStatus", transition.paymentStatus().name())
                .param("reason", nameOrNull(transition.reason()))
                .param("actorType", actor.type().name())
                .param("actorId", actor.id())
                .param("occurredAt", SqlTime.utc(transition.occurredAt()))
                .update();
    }

    private static StatementSpec withState(StatementSpec statement, Purchase purchase) {
        return statement
                .param("id", purchase.id())
                .param("priceCents", centsOrNull(purchase.price()))
                .param("status", purchase.status().name())
                .param("paymentStatus", purchase.paymentStatus().name())
                .param("chargeId", purchase.chargeId())
                .param("paymentCode", purchase.paymentCode())
                .param("paymentDueAt", SqlTime.utcOrNull(purchase.paymentDueAt()))
                .param("paidAt", SqlTime.utcOrNull(purchase.paidAt()))
                .param("paidAmountCents", centsOrNull(purchase.paidAmount()))
                .param("completedAt", SqlTime.utcOrNull(purchase.completedAt()))
                .param("closedReason", nameOrNull(purchase.closedReason()))
                .param("documentKey", purchase.documentKey())
                .param("version", purchase.version())
                .param("updatedAt", SqlTime.utc(purchase.updatedAt()));
    }

    private static Purchase purchase(ResultSet row) throws SQLException {
        return Purchase.builder()
                .id(row.getObject("id", UUID.class))
                .customerId(row.getObject("customer_id", UUID.class))
                .customerSubject(row.getString("customer_subject"))
                .vehicleId(row.getObject("vehicle_id", UUID.class))
                .price(moneyOrNull(row, "price_cents"))
                .status(PurchaseStatus.valueOf(row.getString("status")))
                .paymentStatus(PaymentStatus.valueOf(row.getString("payment_status")))
                .chargeId(row.getString("charge_id"))
                .paymentCode(row.getString("payment_code"))
                .paymentDueAt(SqlTime.instantOrNull(row, "payment_due_at"))
                .paidAt(SqlTime.instantOrNull(row, "paid_at"))
                .paidAmount(moneyOrNull(row, "paid_amount_cents"))
                .completedAt(SqlTime.instantOrNull(row, "completed_at"))
                .closedReason(enumOrNull(ClosedReason.class, row.getString("closed_reason")))
                .documentKey(row.getString("document_key"))
                .version(row.getInt("version"))
                .createdAt(SqlTime.instantOrNull(row, "created_at"))
                .updatedAt(SqlTime.instantOrNull(row, "updated_at"))
                .build();
    }

    private static Long centsOrNull(Money money) {
        return money != null ? money.toCents() : null;
    }

    private static Money moneyOrNull(ResultSet row, String column) throws SQLException {
        long cents = row.getLong(column);
        return row.wasNull() ? null : Money.ofCents(cents);
    }

    private static String nameOrNull(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
        return name != null ? Enum.valueOf(type, name) : null;
    }
}
