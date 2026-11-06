package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.time.SqlTime;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(SalesTestSqs.class)
@Transactional
class SalesSchemaTests {

    private static final OffsetDateTime NOW = SqlTime.utc(Instant.parse("2026-11-01T12:00:00Z"));
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final long PRICE_CENTS = 8_990_000;

    @Autowired
    private JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "sales");
    }

    @Test
    void theApplicationConnectsWithTheAppRole() {
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo("sales_app");
    }

    @Test
    void theMigrationRanWithTheOwnerRole() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAs("sales_owner", "sales");
                Statement statement = owner.createStatement()) {
            assertThat(single(statement, "SELECT installed_by FROM flyway_schema_history WHERE version = '1'"))
                    .isEqualTo("sales_owner");
            for (String table : List.of("purchase", "purchase_transition", "outbox_message", "processed_message",
                    "audit_log", "idempotency_key")) {
                assertThat(single(statement, "SELECT tableowner FROM pg_tables WHERE tablename = '" + table + "'"))
                        .as(table)
                        .isEqualTo("sales_owner");
            }
        }
    }

    @Test
    void aNewPurchaseIsPendingWithoutPayment() {
        UUID id = insert(Map.of());

        Map<String, Object> row = jdbc.sql("SELECT status, payment_status, version FROM purchase WHERE id = :id")
                .param("id", id)
                .query()
                .singleRow();

        assertThat(row).containsEntry("status", "PENDING").containsEntry("payment_status", "NONE")
                .containsEntry("version", 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE purchase_transition SET reason = 'alterado'",
            "DELETE FROM purchase_transition",
            "TRUNCATE purchase_transition",
            "UPDATE audit_log SET summary = 'alterado'",
            "DELETE FROM audit_log",
            "TRUNCATE audit_log",
            "ALTER TABLE purchase ADD COLUMN extra integer",
            "DROP TABLE outbox_message",
            "CREATE TABLE extra (id integer)"})
    void theAppRoleCannotRewriteTheRecordsNorChangeTheSchema(String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update())
                .rootCause()
                .isInstanceOf(SQLException.class)
                .extracting(cause -> ((SQLException) cause).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void theAppRoleAppendsToTheTransitionsAndTheAuditLog() {
        UUID id = insert(Map.of());

        int transitions = jdbc.sql("""
                        INSERT INTO purchase_transition (purchase_id, from_status, to_status, payment_status, reason,
                                                         actor_type, actor_id, occurred_at)
                        VALUES (:id, NULL, 'PENDING', 'NONE', 'PURCHASE_REQUESTED', 'USER', :actor, :at)""")
                .param("id", id)
                .param("actor", UUID.randomUUID().toString())
                .param("at", NOW)
                .update();
        int audit = jdbc.sql("""
                        INSERT INTO audit_log (occurred_at, actor_type, action, entity_type, entity_id, summary)
                        VALUES (:at, 'SYSTEM', 'PURCHASE_CREATED', 'PURCHASE', :id, 'Compra criada')""")
                .param("at", NOW)
                .param("id", id)
                .update();

        assertThat(transitions).isEqualTo(1);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    void aTransitionBelongsToAnExistingPurchase() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO purchase_transition (purchase_id, to_status, payment_status, actor_type, occurred_at)
                        VALUES (:id, 'PENDING', 'NONE', 'SYSTEM', :at)""")
                .param("id", UUID.randomUUID())
                .param("at", NOW)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("purchase_transition_purchase_id_fkey");
    }

    @Test
    void aTransitionHasAKnownActorType() {
        UUID id = insert(Map.of());

        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO purchase_transition (purchase_id, to_status, payment_status, actor_type, occurred_at)
                        VALUES (:id, 'PENDING', 'NONE', 'ANONYMOUS', :at)""")
                .param("id", id)
                .param("at", NOW)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("purchase_transition_actor_type_check");
    }

    static Stream<Arguments> inconsistentPurchases() {
        return Stream.of(
                arguments("purchase_status_valid", with(reserved(), "status", "DONE")),
                arguments("purchase_payment_status_valid", Map.of("payment_status", "CHARGED")),
                arguments("purchase_closed_reason_valid", with(closed("FAILED", "TIMEOUT"), "price_cents", PRICE_CENTS)),
                arguments("purchase_price_cents_check", with(reserved(), "price_cents", 0L)),
                arguments("purchase_reserved_has_price", Map.of("status", "RESERVED")),
                arguments("purchase_reserved_has_price", without(awaiting(), "price_cents")),
                arguments("purchase_reserved_has_price", Map.of("status", "PAID", "payment_status", "PAID")),
                arguments("purchase_reserved_has_price", Map.of("status", "FAILED")),
                arguments("purchase_reserved_has_price", Map.of("status", "FAILED", "closed_reason", "CHARGE_FAILED")),
                arguments("purchase_reserved_has_price", Map.of("status", "EXPIRED", "closed_reason", "PAYMENT_EXPIRED")),
                arguments("purchase_awaiting_has_charge", without(awaiting(), "charge_id")),
                arguments("purchase_awaiting_has_charge", without(awaiting(), "payment_code")),
                arguments("purchase_awaiting_has_charge", without(awaiting(), "payment_due_at")));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("inconsistentPurchases")
    void theDatabaseRefusesAnInconsistentPurchase(String constraint, Map<String, Object> overrides) {
        assertThatThrownBy(() -> insert(overrides))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    static Stream<Map<String, Object>> purchasesClosedWithoutPrice() {
        return Stream.of(
                closed("REJECTED", "VEHICLE_UNAVAILABLE"),
                closed("FAILED", "RESERVATION_FAILED"),
                closed("FAILED", "RECOVERY_TIMEOUT"));
    }

    @ParameterizedTest
    @MethodSource("purchasesClosedWithoutPrice")
    void onlyThePurchaseThatNeverGotTheReservationClosesWithoutPrice(Map<String, Object> overrides) {
        UUID id = insert(overrides);

        assertThat(jdbc.sql("SELECT price_cents IS NULL FROM purchase WHERE id = :id")
                .param("id", id)
                .query(Boolean.class)
                .single()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RESERVED", "AWAITING_PAYMENT"})
    void aCustomerHasOneActivePurchase(String status) {
        UUID customer = UUID.randomUUID();
        insert(Map.of("customer_id", customer));

        assertThatThrownBy(() -> insert(with(inStatus(status), "customer_id", customer)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("purchase_one_active_per_customer");
    }

    @Test
    void aCustomerBuysAgainAfterTheLastPurchaseClosed() {
        UUID customer = UUID.randomUUID();
        insert(with(closedWithPrice("EXPIRED", "PAYMENT_EXPIRED"), "customer_id", customer));
        insert(with(paid("COMPLETED"), "customer_id", customer));

        UUID next = insert(Map.of("customer_id", customer));

        assertThat(next).isNotNull();
    }

    static Stream<Arguments> twoHoldersOfTheSameVehicle() {
        List<String> holders = List.of("RESERVED", "AWAITING_PAYMENT", "PAID", "COMPLETED");
        return holders.stream().flatMap(first -> holders.stream().map(second -> arguments(first, second)));
    }

    @ParameterizedTest(name = "{0} e {1}")
    @MethodSource("twoHoldersOfTheSameVehicle")
    void aVehicleHasOneHolder(String first, String second) {
        UUID vehicle = UUID.randomUUID();
        insert(with(inStatus(first), "vehicle_id", vehicle));

        assertThatThrownBy(() -> insert(with(inStatus(second), "vehicle_id", vehicle)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("purchase_one_holder_per_vehicle");
    }

    @Test
    void twoCustomersAskForTheSameVehicleAndTheClosedOnesDoNotHoldIt() {
        UUID vehicle = UUID.randomUUID();
        insert(Map.of("vehicle_id", vehicle));
        insert(Map.of("vehicle_id", vehicle));
        insert(with(closed("REJECTED", "VEHICLE_UNAVAILABLE"), "vehicle_id", vehicle));
        insert(with(closedWithPrice("CANCELLED", "CUSTOMER_GAVE_UP"), "vehicle_id", vehicle));

        UUID holder = insert(with(reserved(), "vehicle_id", vehicle));

        assertThat(jdbc.sql("SELECT count(*) FROM purchase WHERE vehicle_id = :vehicle")
                .param("vehicle", vehicle)
                .query(Integer.class)
                .single()).isEqualTo(5);
        assertThat(holder).isNotNull();
    }

    @Test
    void aChargeBelongsToOnePurchase() {
        Map<String, Object> first = awaiting();
        insert(first);

        assertThatThrownBy(() -> insert(with(awaiting(), "charge_id", first.get("charge_id"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("purchase_charge_key");
    }

    @Test
    void theOutboxTakesOnlyKnownDestinations() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO outbox_message (id, destination, type, aggregate_id, group_key, payload,
                                                    available_at, created_at)
                        VALUES (:id, 'EMAIL', 'ReleaseVehicle', :id, :id, '{}', :at, :at)""")
                .param("id", UUID.randomUUID())
                .param("at", NOW)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("outbox_message_destination_check");
    }

    @Test
    void theAppRoleRecordsProcessedMessagesAndIdempotencyKeys() {
        int processed = jdbc.sql("""
                        INSERT INTO processed_message (message_id, source, processed_at)
                        VALUES (:id, 'payment-webhook', :at)""")
                .param("id", "evt_" + hex())
                .param("at", NOW)
                .update();
        int key = jdbc.sql("""
                        INSERT INTO idempotency_key (actor_id, key, scope, request_hash, created_at)
                        VALUES (:actor, :key, 'POST /purchases', :hash, :at)""")
                .param("actor", UUID.randomUUID().toString())
                .param("key", UUID.randomUUID().toString())
                .param("hash", hex() + hex())
                .param("at", NOW)
                .update();

        assertThat(processed).isEqualTo(1);
        assertThat(key).isEqualTo(1);
    }

    @Test
    void theOnlyPersonalColumnIsTheOwnerSubject() {
        List<String> personal = jdbc.sql("""
                        SELECT c.relname || '.' || a.attname
                          FROM pg_attribute a
                          JOIN pg_class c ON c.oid = a.attrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'public' AND c.relkind = 'r' AND a.attnum > 0 AND NOT a.attisdropped
                           AND col_description(a.attrelid, a.attnum) LIKE '[pessoal]%'""")
                .query(String.class)
                .list();

        assertThat(personal).containsExactly("purchase.customer_subject");
    }

    private UUID insert(Map<String, Object> overrides) {
        Map<String, Object> row = purchase(overrides);
        String columns = String.join(", ", row.keySet());
        String values = ":" + String.join(", :", row.keySet());
        jdbc.sql("INSERT INTO purchase (" + columns + ") VALUES (" + values + ")").params(row).update();
        return (UUID) row.get("id");
    }

    private static Map<String, Object> purchase(Map<String, Object> overrides) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("customer_id", UUID.randomUUID());
        row.put("customer_subject", UUID.randomUUID().toString());
        row.put("vehicle_id", UUID.randomUUID());
        row.put("created_at", NOW);
        row.put("updated_at", NOW);
        row.putAll(overrides);
        row.values().removeIf(value -> value == Absent.VALUE);
        return row;
    }

    private static Map<String, Object> inStatus(String status) {
        return switch (status) {
            case "PENDING" -> Map.of();
            case "RESERVED" -> reserved();
            case "AWAITING_PAYMENT" -> awaiting();
            default -> paid(status);
        };
    }

    private static Map<String, Object> reserved() {
        return Map.of("status", "RESERVED", "price_cents", PRICE_CENTS);
    }

    private static Map<String, Object> awaiting() {
        return Map.of("status", "AWAITING_PAYMENT", "payment_status", "PENDING", "price_cents", PRICE_CENTS,
                "charge_id", "chg_" + hex(), "payment_code", "00020126580014br.gov.bcb.pix6304ABCD",
                "payment_due_at", NOW.plusMinutes(30));
    }

    private static Map<String, Object> paid(String status) {
        return Map.of("status", status, "payment_status", "PAID", "price_cents", PRICE_CENTS, "charge_id",
                "chg_" + hex(), "paid_at", NOW, "paid_amount_cents", PRICE_CENTS);
    }

    private static Map<String, Object> closed(String status, String reason) {
        return Map.of("status", status, "closed_reason", reason);
    }

    private static Map<String, Object> closedWithPrice(String status, String reason) {
        return with(closed(status, reason), "price_cents", PRICE_CENTS);
    }

    private static Map<String, Object> with(Map<String, Object> base, String column, Object value) {
        Map<String, Object> row = new HashMap<>(base);
        row.put(column, value);
        return row;
    }

    private static Map<String, Object> without(Map<String, Object> base, String column) {
        return with(base, column, Absent.VALUE);
    }

    private static String hex() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String single(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    /** Marca a coluna que a linha não leva, para cair no valor padrão ou ficar nula. */
    private enum Absent {
        VALUE
    }
}
