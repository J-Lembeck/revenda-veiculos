package com.revenda.payments.domain.ports.in;

public interface CreateChargeUseCase {

    /**
     * A cobrança da referência: nova, ou a que já existe quando o pedido se repete com o mesmo valor. Outro valor para a
     * mesma referência sai como {@code CHARGE_REFERENCE_CONFLICT}.
     */
    ChargeCreation create(CreateChargeCommand command);
}
