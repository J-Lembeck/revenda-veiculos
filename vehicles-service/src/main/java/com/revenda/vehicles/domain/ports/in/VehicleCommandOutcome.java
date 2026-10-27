package com.revenda.vehicles.domain.ports.in;

/** O que um comando da compra fez com o estoque. Nos três casos a mensagem está tratada e sai da fila. */
public enum VehicleCommandOutcome {
    APPLIED,
    DUPLICATE,
    NO_EFFECT
}
