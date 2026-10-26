package com.revenda.auditprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.auditprobe.AuditProbeApplication.ProbeAuditAction;
import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.common.test.PostgresTestDatabase;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = AuditProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:audit-probe-schema.sql"})
class JdbcAuditRecorderTests {

    private static final String SELLER = "5e1c7a92-vendedora";

    @Autowired
    private AuditRecorder audit;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient database;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void refusesToRecordOutsideATransaction() {
        UUID vehicleId = UUID.randomUUID();

        assertThatThrownBy(() -> ActorContextHolder.callAs(seller(), () -> {
            audit.record(created(vehicleId));
            return null;
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(entriesOf(vehicleId)).isZero();
    }

    @Test
    void recordsTheActorTheEntityTheTimeAndTheSnapshots() {
        UUID vehicleId = UUID.randomUUID();

        inTransactionAs(seller(), () -> audit.record(
                AuditEntry.of(ProbeAuditAction.VEHICLE_CREATED, "VEHICLE", vehicleId, "Cadastrou o veículo ABC1D23")
                        .withReason("Entrada no estoque")
                        .withChange(null, new VehicleView("ABC1D23", 8990000, "AVAILABLE"))
                        .withExtra(new Origin("cadastro"))));

        Map<String, Object> row = rowOf(vehicleId);
        assertThat(row)
                .containsEntry("actor_type", "USER")
                .containsEntry("actor_id", SELLER)
                .containsEntry("actor_role", "SELLER")
                .containsEntry("action", "VEHICLE_CREATED")
                .containsEntry("entity_type", "VEHICLE")
                .containsEntry("summary", "Cadastrou o veículo ABC1D23")
                .containsEntry("reason", "Entrada no estoque")
                .containsEntry("request_id", "req-" + SELLER)
                .containsEntry("trace_id", null)
                .containsEntry("has_before", false)
                .containsEntry("price_cents", "8990000")
                .containsEntry("status", "AVAILABLE")
                .containsEntry("origin", "cadastro");
        assertThat(((Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(AuditProbeApplication.NOW);
    }

    @Test
    void entryWithoutSnapshotsLeavesDataEmpty() {
        UUID vehicleId = UUID.randomUUID();

        inTransactionAs(seller(), () -> audit.record(created(vehicleId)));

        assertThat(rowOf(vehicleId)).containsEntry("data", null).containsEntry("reason", null);
    }

    @Test
    void serviceIsRecordedByItsClientWithoutRole() {
        UUID vehicleId = UUID.randomUUID();
        ActorContext sales = new ActorContext(ActorType.SERVICE, "sales-service", null, "req-servico-sales");

        inTransactionAs(sales, () -> audit.record(
                AuditEntry.of(ProbeAuditAction.VEHICLE_RESERVED, "VEHICLE", vehicleId, "Reservou o veículo")));

        assertThat(rowOf(vehicleId))
                .containsEntry("actor_type", "SERVICE")
                .containsEntry("actor_id", "sales-service")
                .containsEntry("actor_role", null);
    }

    @Test
    void outsideARequestTheActorIsTheSystem() {
        UUID vehicleId = UUID.randomUUID();

        transaction.executeWithoutResult(status -> audit.record(created(vehicleId)));

        assertThat(rowOf(vehicleId))
                .containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_id", null)
                .containsEntry("request_id", null);
    }

    @Test
    void anonymousActorIsRefusedAndNothingIsRecorded() {
        UUID vehicleId = UUID.randomUUID();

        assertThatThrownBy(() -> inTransactionAs(ActorContext.anonymous("req-anonima-01"),
                () -> audit.record(created(vehicleId))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(entriesOf(vehicleId)).isZero();
    }

    @Test
    void publicRouteDeclaresTheSystemAndKeepsTheRequestId() {
        String requestId = "req-webhook-" + UUID.randomUUID();

        inTransactionAs(ActorContext.anonymous(requestId).asSystem(), () -> audit.record(
                AuditEntry.of(ProbeAuditAction.PAYMENT_WEBHOOK_REJECTED, "PAYMENT_WEBHOOK", null,
                        "Webhook de pagamento recusado: assinatura inválida")));

        Map<String, Object> row = database.sql("""
                        SELECT actor_type, actor_id, entity_id FROM audit_log WHERE request_id = :requestId
                        """)
                .param("requestId", requestId).query().singleRow();
        assertThat(row).containsEntry("actor_type", "SYSTEM").containsEntry("actor_id", null)
                .containsEntry("entity_id", null);
    }

    @Test
    void failureAfterTheAuditUndoesTheActionAndTheEntry() {
        UUID vehicleId = UUID.randomUUID();

        assertThatThrownBy(() -> inTransactionAs(seller(), () -> {
            database.sql("INSERT INTO probe_vehicle (id, plate) VALUES (:id, 'ABC1D23')")
                    .param("id", vehicleId).update();
            audit.record(created(vehicleId));
            throw new IllegalStateException("a ação falhou depois de auditar");
        })).isInstanceOf(IllegalStateException.class).hasMessage("a ação falhou depois de auditar");

        assertThat(entriesOf(vehicleId)).isZero();
        assertThat(database.sql("SELECT count(*) FROM probe_vehicle WHERE id = :id")
                .param("id", vehicleId).query(Integer.class).single()).isZero();
    }

    private void inTransactionAs(ActorContext actor, Runnable work) {
        ActorContextHolder.callAs(actor, () -> {
            transaction.executeWithoutResult(status -> work.run());
            return null;
        });
    }

    private Map<String, Object> rowOf(UUID entityId) {
        return database.sql("""
                        SELECT occurred_at, actor_type, actor_id, actor_role, action, entity_type, summary, reason,
                               data::text AS data, request_id, trace_id,
                               (data -> 'before') IS NOT NULL AS has_before,
                               data -> 'after' ->> 'priceCents' AS price_cents,
                               data -> 'after' ->> 'status' AS status,
                               data -> 'extra' ->> 'origin' AS origin
                          FROM audit_log
                         WHERE entity_id = :entityId
                        """)
                .param("entityId", entityId).query().singleRow();
    }

    private int entriesOf(UUID entityId) {
        return database.sql("SELECT count(*) FROM audit_log WHERE entity_id = :entityId")
                .param("entityId", entityId).query(Integer.class).single();
    }

    private static AuditEntry created(UUID vehicleId) {
        return AuditEntry.of(ProbeAuditAction.VEHICLE_CREATED, "VEHICLE", vehicleId, "Cadastrou o veículo");
    }

    private static ActorContext seller() {
        return new ActorContext(ActorType.USER, SELLER, Role.SELLER, "req-" + SELLER);
    }

    private record VehicleView(String plate, long priceCents, String status) {
    }

    private record Origin(String origin) {
    }
}
