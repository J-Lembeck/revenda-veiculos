package com.revenda.payments.domain.ports.in;

import com.revenda.payments.domain.model.PaymentEvent;

public interface SimulatePaymentUseCase {

    /**
     * Paga a cobrança pendente ou cancelada (o pagamento atrasado) e manda {@code CHARGE_PAID} ao webhook em segundo
     * plano. A paga ou a estornada sai como {@code CHARGE_NOT_PAYABLE}; sem a cobrança, {@code NOT_FOUND}.
     */
    PaymentEvent simulate(SimulatePaymentCommand command);
}
