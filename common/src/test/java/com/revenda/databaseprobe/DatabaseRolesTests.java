package com.revenda.databaseprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.databaseprobe.DatabaseProbeApplication.ProbeAuditAction;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = DatabaseProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.locations=classpath:db/database-probe")
class DatabaseRolesTests {

    private static final String PERMISSION_DENIED = "42501";

    @Autowired
    private JdbcClient database;

    @Autowired
    private AuditRecorder audit;

    @Autowired
    private TransactionTemplate transaction;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "vehicles");
    }

    @Test
    void applicationConnectsWithTheAppRole() {
        assertThat(database.sql("SELECT current_user").query(String.class).single()).isEqualTo("vehicles_app");
    }

    @Test
    void migrationRanWithTheOwnerRole() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAs("vehicles_owner", "vehicles");
             ResultSet history = owner.createStatement()
                     .executeQuery("SELECT installed_by FROM flyway_schema_history WHERE version = '1'")) {
            assertThat(history.next()).isTrue();
            assertThat(history.getString("installed_by")).isEqualTo("vehicles_owner");
        }
        assertThat(database.sql("SELECT tableowner FROM pg_tables WHERE tablename = 'audit_log'")
                .query(String.class).single()).isEqualTo("vehicles_owner");
    }

    @Test
    void migrationHistoryIsClosedToTheAppRole() {
        assertThatThrownBy(() -> database.sql("SELECT count(*) FROM flyway_schema_history").query(Long.class).single())
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo(PERMISSION_DENIED));
    }

    @Test
    void appRoleReadsAndWritesTheServiceTables() {
        UUID vehicleId = UUID.randomUUID();

        database.sql("INSERT INTO probe_vehicle (id, plate) VALUES (:id, 'ABC1D23')").param("id", vehicleId).update();
        database.sql("UPDATE probe_vehicle SET plate = 'XYZ9K87' WHERE id = :id").param("id", vehicleId).update();

        assertThat(plateOf(vehicleId)).isEqualTo("XYZ9K87");
        assertThat(database.sql("DELETE FROM probe_vehicle WHERE id = :id").param("id", vehicleId).update())
                .isEqualTo(1);
    }

    @Test
    void auditLogTakesNewEntriesFromTheAppRole() {
        UUID vehicleId = UUID.randomUUID();

        ActorContextHolder.callAs(ActorContext.system(), () -> transaction.execute(status -> {
            audit.record(AuditEntry.of(ProbeAuditAction.VEHICLE_CREATED, "VEHICLE", vehicleId, "Cadastrou o veículo"));
            return null;
        }));

        assertThat(database.sql("SELECT count(*) FROM audit_log WHERE entity_id = :id").param("id", vehicleId)
                .query(Long.class).single()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE audit_log SET summary = 'reescrito'",
            "DELETE FROM audit_log",
            "TRUNCATE audit_log"})
    void auditLogNeverChangesThroughTheAppRole(String statement) {
        assertThatThrownBy(() -> database.sql(statement).update())
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo(PERMISSION_DENIED));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE TABLE probe_intruder (id uuid)",
            "ALTER TABLE probe_vehicle ADD COLUMN color varchar(30)",
            "DROP TABLE probe_vehicle"})
    void appRoleNeverChangesTheSchema(String statement) {
        assertThatThrownBy(() -> database.sql(statement).update())
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo(PERMISSION_DENIED));
    }

    @Test
    void appRoleNeverEntersAnotherServiceDatabase() {
        assertThatThrownBy(() -> PostgresTestDatabase.connectAs("vehicles_app", "customers").close())
                .isInstanceOfSatisfying(SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo(PERMISSION_DENIED));
    }

    private String plateOf(UUID vehicleId) {
        return database.sql("SELECT plate FROM probe_vehicle WHERE id = :id").param("id", vehicleId)
                .query(String.class).single();
    }
}
