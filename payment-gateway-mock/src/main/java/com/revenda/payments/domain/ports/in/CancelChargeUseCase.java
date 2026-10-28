package com.revenda.payments.domain.ports.in;

import com.revenda.payments.domain.model.Charge;

public interface CancelChargeUseCase {

    /** Cancela a cobrança pendente; em qualquer outro estado, não muda nada. Sem a cobrança, {@code NOT_FOUND}. */
    Charge cancel(String chargeId);
}
