package com.revenda.vehicles.infrastructure.adapters.out.persistence;

import com.revenda.common.time.SqlTime;
import com.revenda.vehicles.domain.ports.out.SaveProcessedMessagePort;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcProcessedMessageStore implements SaveProcessedMessagePort {

    private final JdbcClient jdbc;

    @Override
    public boolean markProcessed(UUID messageId, String source, Instant now) {
        return jdbc.sql("""
                        INSERT INTO processed_message (message_id, source, processed_at)
                        VALUES (:messageId, :source, :now)
                        ON CONFLICT (message_id) DO NOTHING""")
                .param("messageId", messageId.toString())
                .param("source", source)
                .param("now", SqlTime.utc(now))
                .update() == 1;
    }
}
