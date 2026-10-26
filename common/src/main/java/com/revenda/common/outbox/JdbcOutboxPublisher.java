package com.revenda.common.outbox;

import com.revenda.common.jdbc.Transactions;
import com.revenda.common.time.SqlTime;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class JdbcOutboxPublisher implements OutboxPublisher {

    private static final String INSERT = """
            INSERT INTO outbox_message (id, destination, type, aggregate_id, group_key, payload, available_at,
                                        created_at)
            VALUES (:id, :destination, :type, :aggregateId, :groupKey, CAST(:payload AS jsonb), :now, :now)
            """;

    private final DataSource dataSource;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public UUID publish(NewOutboxMessage message) {
        Transactions.requireOpen(dataSource,
                "A mensagem do outbox só é gravada dentro da transação da mudança de estado");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        jdbcClient.sql(INSERT)
                .param("id", id)
                .param("destination", message.destination().name())
                .param("type", message.type())
                .param("aggregateId", message.aggregateId())
                .param("groupKey", message.groupKey())
                .param("payload", objectMapper.writeValueAsString(message.payload()))
                .param("now", SqlTime.utc(now))
                .update();
        return id;
    }
}
