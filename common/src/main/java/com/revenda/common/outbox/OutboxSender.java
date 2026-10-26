package com.revenda.common.outbox;

/**
 * A porta de envio de um destino. Roda sem transação aberta; para tentar de novo mais tarde, lança
 * exceção. O mesmo id pode chegar duas vezes: quem recebe descarta a repetição.
 */
public interface OutboxSender {

    OutboxDestination destination();

    void send(OutboxMessage message);
}
