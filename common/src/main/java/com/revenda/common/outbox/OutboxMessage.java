package com.revenda.common.outbox;

import java.time.Instant;
import java.util.UUID;

/** A mensagem como o {@link OutboxSender} a recebe; {@code payload} é o JSON gravado. */
public record OutboxMessage(
        UUID id,
        String destination,
        String type,
        UUID aggregateId,
        String groupKey,
        String payload,
        int attempts,
        Instant createdAt) {
}
