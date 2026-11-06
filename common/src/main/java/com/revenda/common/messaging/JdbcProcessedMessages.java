package com.revenda.common.messaging;

import com.revenda.common.jdbc.Transactions;
import com.revenda.common.time.SqlTime;
import java.time.Instant;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

@RequiredArgsConstructor
public class JdbcProcessedMessages implements ProcessedMessages {

    private final DataSource dataSource;
    private final JdbcClient jdbcClient;

    @Override
    public boolean markProcessed(String messageId, String source, Instant now) {
        if (messageId == null || messageId.isBlank() || messageId.length() > MESSAGE_ID_LIMIT) {
            throw new IllegalArgumentException("Id de mensagem vazio ou com mais de " + MESSAGE_ID_LIMIT
                    + " caracteres");
        }
        if (source == null || source.isBlank() || source.length() > SOURCE_LIMIT) {
            throw new IllegalArgumentException("Origem vazia ou com mais de " + SOURCE_LIMIT + " caracteres");
        }
        Transactions.requireOpen(dataSource, "A mensagem tratada só é gravada dentro da transação do efeito");
        return jdbcClient.sql("""
                        INSERT INTO processed_message (message_id, source, processed_at)
                        VALUES (:messageId, :source, :now)
                        ON CONFLICT (message_id) DO NOTHING""")
                .param("messageId", messageId)
                .param("source", source)
                .param("now", SqlTime.utc(now))
                .update() == 1;
    }
}
