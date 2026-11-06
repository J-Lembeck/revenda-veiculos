package com.revenda.sales.domain.model;

public enum PaymentStatus {
    NONE,
    PENDING,
    PAID,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED;

    /** O dinheiro já chegou, fique ou volte: o pagamento desta cobrança já foi registrado. */
    public boolean isReceived() {
        return this == PAID || this == REFUND_REQUESTED || this == REFUNDED;
    }
}
