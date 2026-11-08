package com.revenda.customers.infrastructure.adapters.out.persistence;

import com.revenda.common.time.SqlTime;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerAccess;
import com.revenda.customers.domain.ports.out.LoadCustomerPrivacyPort;
import com.revenda.customers.domain.ports.out.SaveCustomerPrivacyPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * O lado LGPD do cadastro: o histórico vem do {@code audit_log}, sem o {@code sub} de quem fez; anonimizar apaga
 * cada coluna pessoal e a conta e mantém o {@code id}, como a restrição {@code customer_anonymized_is_empty} exige.
 */
@Repository
@RequiredArgsConstructor
class JdbcCustomerPrivacyStore implements LoadCustomerPrivacyPort, SaveCustomerPrivacyPort {

    private final JdbcClient jdbc;

    @Override
    public List<CustomerAccess> historyOf(UUID customerId, String owner) {
        return jdbc.sql("""
                        SELECT occurred_at, action, actor_type, actor_id, actor_role, summary, reason,
                               data -> 'extra' ->> 'purchaseId' AS purchase_id
                          FROM audit_log
                         WHERE entity_type = :entityType AND entity_id = :id
                         ORDER BY occurred_at, id""")
                .param("entityType", Customer.ENTITY_TYPE)
                .param("id", customerId)
                .query((row, index) -> access(row, owner))
                .list();
    }

    @Override
    public Optional<Instant> deletionRequestedAt(UUID customerId) {
        return jdbc.sql("SELECT deletion_requested_at FROM customer WHERE id = :id AND status = 'ACTIVE'")
                .param("id", customerId)
                .query((row, index) -> Optional.ofNullable(SqlTime.instantOrNull(row, "deletion_requested_at")))
                .optional()
                .flatMap(requested -> requested);
    }

    @Override
    public List<UUID> deletionRequested() {
        return jdbc.sql("""
                        SELECT id
                          FROM customer
                         WHERE status = 'ACTIVE' AND deletion_requested_at IS NOT NULL
                         ORDER BY deletion_requested_at, id""")
                .query(UUID.class)
                .list();
    }

    @Override
    public boolean requestDeletion(UUID customerId, Instant at) {
        return jdbc.sql("""
                        UPDATE customer
                           SET deletion_requested_at = :at, version = version + 1, updated_at = :at
                         WHERE id = :id AND status = 'ACTIVE' AND deletion_requested_at IS NULL""")
                .param("at", SqlTime.utc(at))
                .param("id", customerId)
                .update() == 1;
    }

    @Override
    public boolean anonymize(UUID customerId, Instant at) {
        return jdbc.sql("""
                        UPDATE customer
                           SET status = 'ANONYMIZED', anonymized_at = :at, identity_subject = NULL, full_name = NULL,
                               cpf_enc = NULL, cpf_index = NULL, email_enc = NULL, phone_enc = NULL,
                               birth_date_enc = NULL, address_enc = NULL, version = version + 1, updated_at = :at
                         WHERE id = :id AND status = 'ACTIVE'""")
                .param("at", SqlTime.utc(at))
                .param("id", customerId)
                .update() == 1;
    }

    private static CustomerAccess access(ResultSet row, String owner) throws SQLException {
        String actorType = row.getString("actor_type");
        String actorId = row.getString("actor_id");
        String role = row.getString("actor_role");
        String purchase = row.getString("purchase_id");
        CustomerAccess.Actor actor = switch (actorType) {
            case "USER" -> owner.equals(actorId) ? CustomerAccess.Actor.OWNER : CustomerAccess.Actor.STAFF;
            case "SERVICE" -> CustomerAccess.Actor.SERVICE;
            default -> CustomerAccess.Actor.SYSTEM;
        };
        return new CustomerAccess(
                SqlTime.instantOrNull(row, "occurred_at"),
                row.getString("action"),
                actor,
                actor == CustomerAccess.Actor.STAFF ? role : null,
                actor == CustomerAccess.Actor.SERVICE ? actorId : null,
                row.getString("summary"),
                row.getString("reason"),
                purchase == null ? null : UUID.fromString(purchase));
    }
}
