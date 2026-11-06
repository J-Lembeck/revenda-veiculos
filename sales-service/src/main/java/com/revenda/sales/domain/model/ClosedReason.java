package com.revenda.sales.domain.model;

/** Por que a compra encerrou sem chegar à retirada. */
public enum ClosedReason {
    VEHICLE_UNAVAILABLE,
    RESERVATION_FAILED,
    CHARGE_FAILED,
    PAYMENT_EXPIRED,
    PAYMENT_MISMATCH,
    CUSTOMER_GAVE_UP,
    RECOVERY_TIMEOUT
}
