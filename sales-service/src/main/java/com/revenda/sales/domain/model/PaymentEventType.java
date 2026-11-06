package com.revenda.sales.domain.model;

/** O que o gateway avisa pelo webhook. */
public enum PaymentEventType {
    CHARGE_PAID,
    CHARGE_REFUNDED
}
