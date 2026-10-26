package com.revenda.common.audit;

import com.revenda.common.jdbc.Transactions;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.time.SqlTime;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class JdbcAuditRecorder implements AuditRecorder {

    private static final String INSERT = """
            INSERT INTO audit_log (occurred_at, actor_type, actor_id, actor_role, action, entity_type, entity_id,
                                   summary, reason, data, request_id)
            VALUES (:occurredAt, :actorType, :actorId, :actorRole, :action, :entityType, :entityId,
                    :summary, :reason, CAST(:data AS jsonb), :requestId)
            """;

    private final DataSource dataSource;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void record(AuditEntry entry) {
        Transactions.requireOpen(dataSource, "A auditoria só é gravada dentro da transação da ação");
        ActorContext actor = requireIdentified(ActorContextHolder.get());
        jdbcClient.sql(INSERT)
                .param("occurredAt", SqlTime.utc(clock.instant()))
                .param("actorType", actor.type().name())
                .param("actorId", actor.id())
                .param("actorRole", actor.role() != null ? actor.role().name() : null)
                .param("action", entry.action().name())
                .param("entityType", entry.entityType())
                .param("entityId", entry.entityId())
                .param("summary", entry.summary())
                .param("reason", entry.reason())
                .param("data", data(entry))
                .param("requestId", actor.requestId())
                .update();
    }

    private static ActorContext requireIdentified(ActorContext actor) {
        if (actor.type() == ActorType.ANONYMOUS) {
            throw new IllegalStateException(
                    "A auditoria não aceita ator anônimo: a rota pública declara o ator antes de auditar");
        }
        return actor;
    }

    private String data(AuditEntry entry) {
        Map<String, Record> data = new LinkedHashMap<>();
        if (entry.before() != null) {
            data.put("before", entry.before());
        }
        if (entry.after() != null) {
            data.put("after", entry.after());
        }
        if (entry.extra() != null) {
            data.put("extra", entry.extra());
        }
        return data.isEmpty() ? null : objectMapper.writeValueAsString(data);
    }
}
