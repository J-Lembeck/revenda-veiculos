package com.revenda.sales.domain.model;

/** Para onde vai cada comando da saga; o nome é o destino gravado no outbox. */
public enum CommandDestination {
    VEHICLE_COMMANDS,
    PAYMENT_GATEWAY
}
