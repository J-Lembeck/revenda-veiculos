package com.revenda.vehicles.domain.ports.out;

import java.time.Instant;
import java.util.UUID;

public interface SaveProcessedMessagePort {

    String VEHICLE_COMMANDS = "vehicle-commands";

    /**
     * Guarda o id da mensagem na transação aberta e devolve se é a primeira vez; repetida, devolve {@code false} sem
     * gravar. Se a transação desfaz, a mensagem volta a contar como não processada.
     */
    boolean markProcessed(UUID messageId, String source, Instant now);
}
