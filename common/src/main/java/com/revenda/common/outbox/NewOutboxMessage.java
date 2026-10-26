package com.revenda.common.outbox;

import java.util.Objects;
import java.util.UUID;

/**
 * Um comando para outro serviço. {@code payload} é um record explícito que vira JSON — ids, centavos e
 * instantes, nunca dado pessoal. Mensagens do mesmo destino e {@code groupKey} saem na ordem em que foram
 * gravadas.
 */
public record NewOutboxMessage(
        OutboxDestination destination,
        String type,
        UUID aggregateId,
        String groupKey,
        Record payload) {

    public static final int DESTINATION_LIMIT = 20;
    public static final int TYPE_LIMIT = 40;
    public static final int GROUP_KEY_LIMIT = 64;

    public NewOutboxMessage {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(groupKey, "groupKey");
        Objects.requireNonNull(payload, "payload");
        require(destination.name().length() <= DESTINATION_LIMIT,
                "destino com mais de " + DESTINATION_LIMIT + " caracteres");
        require(!type.isBlank() && type.length() <= TYPE_LIMIT,
                "tipo vazio ou com mais de " + TYPE_LIMIT + " caracteres");
        require(!groupKey.isBlank() && groupKey.length() <= GROUP_KEY_LIMIT,
                "grupo vazio ou com mais de " + GROUP_KEY_LIMIT + " caracteres");
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalArgumentException("Mensagem do outbox inválida: " + problem);
        }
    }
}
