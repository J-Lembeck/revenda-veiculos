package com.revenda.payments.domain.ports.in;

import com.revenda.payments.domain.model.PaymentOutcome;

/** Simular o pagamento da cobrança, com o webhook entregue {@code deliveries} vezes (mais de uma: duplicado). */
public record SimulatePaymentCommand(String chargeId, PaymentOutcome outcome, int deliveries) {

    public static final int DELIVERIES_MIN = 1;
    public static final int DELIVERIES_MAX = 3;
}
