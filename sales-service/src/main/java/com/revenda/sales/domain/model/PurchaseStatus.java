package com.revenda.sales.domain.model;

public enum PurchaseStatus {
    PENDING,
    RESERVED,
    AWAITING_PAYMENT,
    PAID,
    COMPLETED,
    REJECTED,
    FAILED,
    EXPIRED,
    CANCELLED;

    /** No meio do caminho síncrono: a reserva ou a cobrança estão em curso e ainda não voltaram ao cliente. */
    public boolean isInFlight() {
        return this == PENDING || this == RESERVED;
    }
}
