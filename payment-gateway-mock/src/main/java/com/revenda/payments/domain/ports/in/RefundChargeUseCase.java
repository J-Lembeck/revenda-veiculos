package com.revenda.payments.domain.ports.in;

import com.revenda.payments.domain.model.Charge;

public interface RefundChargeUseCase {

    /**
     * Estorna a cobrança paga e avisa por {@code CHARGE_REFUNDED}; em qualquer outro estado, não muda nada nem avisa.
     * Sem a cobrança, {@code NOT_FOUND}.
     */
    Charge refund(String chargeId);
}
