package com.revenda.payments.infrastructure.adapters.in.web;

import com.revenda.payments.domain.model.PaymentOutcome;
import com.revenda.payments.domain.ports.in.SimulatePaymentCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** O "cliente pagou": o valor certo ou errado e quantas vezes o webhook chega (sem {@code deliveries}, uma). */
record SimulationRequest(
        @NotNull PaymentOutcome outcome,
        @Min(SimulatePaymentCommand.DELIVERIES_MIN) @Max(SimulatePaymentCommand.DELIVERIES_MAX) Integer deliveries) {

    SimulatePaymentCommand toCommand(String chargeId) {
        return new SimulatePaymentCommand(chargeId, outcome,
                deliveries == null ? SimulatePaymentCommand.DELIVERIES_MIN : deliveries);
    }
}
