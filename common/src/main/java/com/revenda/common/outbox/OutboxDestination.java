package com.revenda.common.outbox;

/** Implementada pelo enum de destinos do serviço; o nome da constante é o que vai para a coluna. */
public interface OutboxDestination {

    String name();
}
