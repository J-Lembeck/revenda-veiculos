package com.revenda.sales.domain.model;

/**
 * Para onde vai cada comando da saga; o nome é o destino gravado no outbox. {@code DOCUMENTS} é o próprio {@code sales}:
 * o documento de transferência sai depois da retirada, com retentativa, como os comandos para os vizinhos.
 */
public enum CommandDestination {
    VEHICLE_COMMANDS,
    PAYMENT_GATEWAY,
    DOCUMENTS
}
