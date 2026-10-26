package com.revenda.common.outbox;

import java.util.UUID;

public interface OutboxPublisher {

    /**
     * Grava a mensagem na transação já aberta da mudança de estado e devolve o id dela, que o destino usa
     * para descartar a repetição. Recusa fora de transação.
     */
    UUID publish(NewOutboxMessage message);
}
