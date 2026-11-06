package com.revenda.sales.domain.model;

/** O motivo de cada linha da linha do tempo; a transição que encerra a compra usa o nome do {@link ClosedReason}. */
public enum TransitionReason {
    PURCHASE_REQUESTED,
    VEHICLE_RESERVED,
    CHARGE_ISSUED,
    PAYMENT_CONFIRMED,
    PICKUP_CONFIRMED,
    LATE_PAYMENT,
    REFUND_CONFIRMED,
    VEHICLE_UNAVAILABLE,
    RESERVATION_FAILED,
    CHARGE_FAILED,
    PAYMENT_EXPIRED,
    PAYMENT_MISMATCH,
    CUSTOMER_GAVE_UP,
    RECOVERY_TIMEOUT;

    public static TransitionReason closing(ClosedReason reason) {
        return valueOf(reason.name());
    }
}
