package com.revenda.common.messaging;

import java.time.Instant;

/**
 * As mensagens já tratadas por quem consome (comando da fila, evento de webhook), em {@code processed_message}: o
 * consumidor guarda o id na mesma transação do efeito e descarta a repetição.
 */
public interface ProcessedMessages {

    int MESSAGE_ID_LIMIT = 100;
    int SOURCE_LIMIT = 30;

    /**
     * Guarda o id da mensagem na transação já aberta e devolve se é a primeira vez; repetida, devolve {@code false} sem
     * gravar. Se a transação desfaz, a mensagem volta a contar como não tratada. Recusa fora de transação.
     */
    boolean markProcessed(String messageId, String source, Instant now);
}
