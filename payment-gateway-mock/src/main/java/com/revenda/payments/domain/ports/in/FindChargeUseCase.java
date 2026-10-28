package com.revenda.payments.domain.ports.in;

import com.revenda.payments.domain.model.Charge;

public interface FindChargeUseCase {

    /** A cobrança da referência; sem ela, {@code NOT_FOUND}. */
    Charge findByReference(String referenceId);
}
