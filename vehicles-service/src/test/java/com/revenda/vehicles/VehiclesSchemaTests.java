package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.time.SqlTime;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "revenda.vehicle-commands.enabled=false")
class VehiclesSchemaTests {

    private static final OffsetDateTime NOW = SqlTime.utc(Instant.parse("2026-10-26T12:00:00Z"));
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    private JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "vehicles");
    }

    @Test
    void theApplicationConnectsWithTheAppRole() {
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo("vehicles_app");
    }

    @Test
    void theMigrationRanWithTheOwnerRole() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAs("vehicles_owner", "vehicles");
                Statement statement = owner.createStatement()) {
            assertThat(single(statement, "SELECT installed_by FROM flyway_schema_history WHERE version = '1'"))
                    .isEqualTo("vehicles_owner");
            assertThat(single(statement, "SELECT tableowner FROM pg_tables WHERE tablename = 'vehicle'"))
                    .isEqualTo("vehicles_owner");
        }
    }

    @Test
    void theAppRoleWritesAndReadsTheVehicles() {
        UUID id = insert(Map.of());

        String status = jdbc.sql("SELECT status FROM vehicle WHERE id = :id").param("id", id).query(String.class).single();

        assertThat(status).isEqualTo("AVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE audit_log SET summary = 'alterado'",
            "DELETE FROM audit_log",
            "TRUNCATE audit_log",
            "ALTER TABLE vehicle ADD COLUMN extra integer",
            "DROP TABLE processed_message"})
    void theAppRoleCannotRewriteTheAuditNorChangeTheSchema(String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update())
                .rootCause()
                .isInstanceOf(SQLException.class)
                .extracting(cause -> ((SQLException) cause).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void theAppRoleAppendsToTheAuditLog() {
        int inserted = jdbc.sql("""
                        INSERT INTO audit_log (occurred_at, actor_type, action, entity_type, summary)
                        VALUES (:at, 'SYSTEM', 'VEHICLE_CREATED', 'VEHICLE', 'Veículo cadastrado')""")
                .param("at", NOW)
                .update();

        assertThat(inserted).isEqualTo(1);
    }

    static Stream<Arguments> inconsistentVehicles() {
        UUID purchase = UUID.randomUUID();
        return Stream.of(
                arguments("vehicle_status_valid", Map.of("status", "LOST", "purchase_id", purchase)),
                arguments("vehicle_purchase_matches_status", Map.of("purchase_id", purchase)),
                arguments("vehicle_purchase_matches_status", Map.of("status", "RESERVED")),
                arguments("vehicle_sale_matches_status", Map.of("status", "SOLD", "purchase_id", purchase)),
                arguments("vehicle_sale_matches_status", Map.of("sold_price_cents", 8_990_000L, "sold_at", NOW)),
                arguments("vehicle_delivery_matches_status", Map.of("status", "DELIVERED", "purchase_id", purchase,
                        "sold_price_cents", 8_990_000L, "sold_at", NOW)),
                arguments("vehicle_delivery_matches_status", Map.of("delivered_at", NOW)),
                arguments("vehicle_model_year_valid", Map.of("manufacture_year", 2020, "model_year", 2022)),
                arguments("vehicle_model_year_valid", Map.of("manufacture_year", 2020, "model_year", 2019)),
                arguments("vehicle_manufacture_year_check", Map.of("manufacture_year", 1899, "model_year", 1899)),
                arguments("vehicle_mileage_km_check", Map.of("mileage_km", -1)),
                arguments("vehicle_price_cents_check", Map.of("price_cents", 0L)),
                arguments("vehicle_price_cents_check", Map.of("price_cents", 1_000_000_001L)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("inconsistentVehicles")
    void theDatabaseRefusesAnInconsistentVehicle(String constraint, Map<String, Object> overrides) {
        assertThatThrownBy(() -> insert(overrides))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void aSoldAndDeliveredVehicleKeepsItsPurchase() {
        UUID id = insert(Map.of("status", "DELIVERED", "purchase_id", UUID.randomUUID(),
                "sold_price_cents", 8_990_000L, "sold_at", NOW, "delivered_at", NOW));

        assertThat(jdbc.sql("SELECT status FROM vehicle WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("DELIVERED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"plate", "vin", "renavam"})
    void plateVinAndRenavamAreUnique(String column) {
        Map<String, Object> first = vehicle(Map.of());
        insertRow(first);

        assertThatThrownBy(() -> insert(Map.of(column, first.get(column))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("vehicle_" + column + "_key");
    }

    private UUID insert(Map<String, Object> overrides) {
        Map<String, Object> row = vehicle(overrides);
        insertRow(row);
        return (UUID) row.get("id");
    }

    private void insertRow(Map<String, Object> row) {
        String columns = String.join(", ", row.keySet());
        String values = ":" + String.join(", :", row.keySet());
        jdbc.sql("INSERT INTO vehicle (" + columns + ") VALUES (" + values + ")").params(row).update();
    }

    private static Map<String, Object> vehicle(Map<String, Object> overrides) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("brand", "Marca Teste");
        row.put("model", "Modelo Teste");
        row.put("manufacture_year", 2022);
        row.put("model_year", 2023);
        row.put("color", "Prata");
        row.put("mileage_km", 35_000);
        row.put("plate", SyntheticData.plate());
        row.put("vin", SyntheticData.vin());
        row.put("renavam", SyntheticData.renavam());
        row.put("price_cents", 8_990_000L);
        row.put("created_at", NOW);
        row.put("updated_at", NOW);
        row.putAll(overrides);
        return row;
    }

    private static String single(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
