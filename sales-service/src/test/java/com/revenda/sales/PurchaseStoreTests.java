package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.PurchasePaths;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PurchaseStoreTests {

    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    private SavePurchasePort save;

    @Autowired
    private LoadPurchasePort load;

    @Autowired
    private LockPurchasePort lock;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "sales");
    }

    static Stream<PurchasePaths.Path> everyPath() {
        return PurchasePaths.all().stream();
    }

    @ParameterizedTest
    @MethodSource("everyPath")
    void everyPathIsAcceptedByTheDatabaseAndReadBackAsTheDomainLeftIt(PurchasePaths.Path path) {
        persist(path);

        UUID id = path.purchase().id();
        assertThat(load.load(id)).contains(path.purchase());
        assertThat(load.loadTransitions(id)).containsExactlyElementsOf(path.transitions());
    }

    @Test
    void eachTransitionKeepsWhoMadeIt() {
        String subject = UUID.randomUUID().toString();
        PurchasePaths.Path reserved = PurchasePaths.reserved();
        UUID id = reserved.purchase().id();
        ActorContext customer = new ActorContext(ActorType.USER, subject, Role.CUSTOMER, "req-1");

        inTransactionAs(customer, () -> save.create(reserved.changes().getFirst()));
        inTransaction(() -> save.update(reserved.changes().get(1)));

        assertThat(jdbc.sql("SELECT actor_type, actor_id FROM purchase_transition WHERE purchase_id = :id ORDER BY id")
                .param("id", id)
                .query((row, index) -> row.getString("actor_type") + " " + row.getString("actor_id"))
                .list()).containsExactly("USER " + subject, "SYSTEM null");
    }

    @Test
    void anAnonymousActorChangesNothing() {
        PurchaseChange requested = PurchasePaths.pending().changes().getFirst();

        assertThatThrownBy(() -> inTransactionAs(ActorContext.anonymous("req-2"), () -> save.create(requested)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(load.load(requested.purchase().id())).isEmpty();
    }

    @Test
    void writingAndLockingNeedTheTransactionOfTheChange() {
        PurchasePaths.Path reserved = PurchasePaths.reserved();
        UUID id = reserved.purchase().id();

        assertThatThrownBy(() -> save.create(reserved.changes().getFirst()))
                .isInstanceOf(IllegalTransactionStateException.class);
        inTransaction(() -> save.create(reserved.changes().getFirst()));
        assertThatThrownBy(() -> save.update(reserved.changes().get(1)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> lock.lock(id)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aChangeFromAnOlderVersionIsRefusedAndLeavesNoTransition() {
        PurchasePaths.Path rejected = PurchasePaths.rejected();
        PurchaseChange stale = rejected.changes().getFirst().purchase()
                .reserve(PurchasePaths.PRICE, PurchasePaths.REQUESTED_AT);
        persist(rejected);

        assertThatThrownBy(() -> inTransaction(() -> save.update(stale))).isInstanceOf(IllegalStateException.class);
        assertThat(load.load(rejected.purchase().id())).contains(rejected.purchase());
        assertThat(load.loadTransitions(rejected.purchase().id())).hasSize(2);
    }

    @Test
    void theLockHoldsTheRowUntilTheTransactionEnds() throws SQLException {
        PurchasePaths.Path awaiting = PurchasePaths.awaiting();
        persist(awaiting);
        UUID id = awaiting.purchase().id();

        try (Connection other = PostgresTestDatabase.connectAs("sales_app", "sales")) {
            String refused = inTransaction(() -> {
                assertThat(lock.lock(id)).contains(awaiting.purchase());
                return lockStateFrom(other, id);
            });
            assertThat(refused).isEqualTo(LOCK_NOT_AVAILABLE);
            assertThat(lockStateFrom(other, id)).isNull();
        }
    }

    @Test
    void anUnknownPurchaseIsEmpty() {
        UUID id = UUID.randomUUID();

        assertThat(load.load(id)).isEmpty();
        assertThat(load.loadTransitions(id)).isEmpty();
        assertThat(inTransaction(() -> lock.lock(id))).isEmpty();
    }

    @Test
    void theRowKeepsTheOwnerIdsAndTheAmountsInCents() {
        PurchasePaths.Path completed = PurchasePaths.completed();
        persist(completed);
        Purchase purchase = completed.purchase();

        Map<String, Object> row = jdbc.sql("SELECT * FROM purchase WHERE id = :id")
                .param("id", purchase.id())
                .query()
                .singleRow();

        assertThat(row).containsEntry("customer_id", purchase.customerId())
                .containsEntry("customer_subject", purchase.customerSubject())
                .containsEntry("price_cents", PurchasePaths.PRICE.toCents())
                .containsEntry("paid_amount_cents", PurchasePaths.PRICE.toCents());
    }

    private void persist(PurchasePaths.Path path) {
        List<PurchaseChange> changes = path.changes();
        inTransaction(() -> save.create(changes.getFirst()));
        for (int step = 1; step < changes.size(); step++) {
            Purchase before = changes.get(step - 1).purchase();
            PurchaseChange change = changes.get(step);
            inTransaction(() -> {
                assertThat(lock.lock(before.id())).contains(before);
                save.update(change);
            });
        }
    }

    private void inTransaction(Runnable work) {
        transaction.executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    private void inTransactionAs(ActorContext actor, Runnable work) {
        ActorContextHolder.callAs(actor, () -> {
            inTransaction(work);
            return null;
        });
    }

    private static String lockStateFrom(Connection other, UUID id) {
        try (PreparedStatement statement = other.prepareStatement("SELECT id FROM purchase WHERE id = ? FOR UPDATE NOWAIT")) {
            statement.setObject(1, id);
            statement.executeQuery().close();
            return null;
        } catch (SQLException refused) {
            return refused.getSQLState();
        }
    }
}
