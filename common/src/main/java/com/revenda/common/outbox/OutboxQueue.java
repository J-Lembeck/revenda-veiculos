package com.revenda.common.outbox;

import com.revenda.common.time.SqlTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A fila do outbox no banco. Só é reivindicada a mensagem mais antiga ainda não enviada de cada destino e
 * grupo: enquanto a anterior estiver pendente, em envio ou {@code FAILED}, a seguinte espera.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class OutboxQueue {

    static final String LEASE_EXPIRED = "LEASE_EXPIRED";

    private final JdbcClient jdbcClient;

    List<Expired> releaseExpired(Instant now, int maxAttempts) {
        return jdbcClient.sql("""
                        UPDATE outbox_message
                           SET status = CASE WHEN attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END,
                               attempts = attempts + 1, available_at = :now, locked_until = NULL,
                               last_error = :error
                         WHERE id IN (SELECT id
                                        FROM outbox_message
                                       WHERE status = 'PROCESSING' AND locked_until < :now
                                         FOR UPDATE SKIP LOCKED)
                        RETURNING id, destination, type, attempts, status = 'FAILED' AS failed
                        """)
                .param("maxAttempts", maxAttempts)
                .param("now", SqlTime.utc(now))
                .param("error", LEASE_EXPIRED)
                .query((row, index) -> new Expired(
                        row.getObject("id", UUID.class),
                        row.getString("destination"),
                        row.getString("type"),
                        row.getInt("attempts"),
                        row.getBoolean("failed")))
                .list();
    }

    List<Claimed> claim(Instant now, Instant lease, int limit) {
        return jdbcClient.sql("""
                        UPDATE outbox_message
                           SET status = 'PROCESSING', locked_until = :lease
                         WHERE id IN (SELECT m.id
                                        FROM outbox_message m
                                       WHERE m.status = 'PENDING' AND m.available_at <= :now
                                         AND NOT EXISTS (SELECT 1
                                                           FROM outbox_message older
                                                          WHERE older.destination = m.destination
                                                            AND older.group_key = m.group_key
                                                            AND older.seq < m.seq
                                                            AND older.status <> 'DONE')
                                       ORDER BY m.seq
                                       LIMIT :limit
                                         FOR UPDATE SKIP LOCKED)
                        RETURNING id, seq, destination, type, aggregate_id, group_key, payload::text AS payload,
                                  attempts, created_at
                        """)
                .param("lease", SqlTime.utc(lease))
                .param("now", SqlTime.utc(now))
                .param("limit", limit)
                .query((row, index) -> new Claimed(
                        new OutboxMessage(
                                row.getObject("id", UUID.class),
                                row.getString("destination"),
                                row.getString("type"),
                                row.getObject("aggregate_id", UUID.class),
                                row.getString("group_key"),
                                row.getString("payload"),
                                row.getInt("attempts"),
                                SqlTime.instantOrNull(row, "created_at")),
                        row.getLong("seq"),
                        lease))
                .list()
                .stream()
                .sorted(Comparator.comparingLong(Claimed::seq))
                .toList();
    }

    boolean markSent(Claimed claimed, Instant now) {
        return jdbcClient.sql("""
                        UPDATE outbox_message
                           SET status = 'DONE', processed_at = :now, locked_until = NULL, last_error = NULL
                         WHERE id = :id AND status = 'PROCESSING' AND locked_until = :lease
                        """)
                .param("now", SqlTime.utc(now))
                .param("id", claimed.message().id())
                .param("lease", SqlTime.utc(claimed.lease()))
                .update() == 1;
    }

    boolean reschedule(Claimed claimed, int attempts, Instant availableAt, String error) {
        return jdbcClient.sql("""
                        UPDATE outbox_message
                           SET status = 'PENDING', attempts = :attempts, available_at = :availableAt,
                               locked_until = NULL, last_error = :error
                         WHERE id = :id AND status = 'PROCESSING' AND locked_until = :lease
                        """)
                .param("attempts", attempts)
                .param("availableAt", SqlTime.utc(availableAt))
                .param("error", error)
                .param("id", claimed.message().id())
                .param("lease", SqlTime.utc(claimed.lease()))
                .update() == 1;
    }

    boolean markFailed(Claimed claimed, int attempts, String error) {
        return jdbcClient.sql("""
                        UPDATE outbox_message
                           SET status = 'FAILED', attempts = :attempts, locked_until = NULL, last_error = :error
                         WHERE id = :id AND status = 'PROCESSING' AND locked_until = :lease
                        """)
                .param("attempts", attempts)
                .param("error", error)
                .param("id", claimed.message().id())
                .param("lease", SqlTime.utc(claimed.lease()))
                .update() == 1;
    }

    record Claimed(OutboxMessage message, long seq, Instant lease) {
    }

    record Expired(UUID id, String destination, String type, int attempts, boolean failed) {
    }
}
