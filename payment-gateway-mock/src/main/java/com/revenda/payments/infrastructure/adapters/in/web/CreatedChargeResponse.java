package com.revenda.payments.infrastructure.adapters.in.web;

import com.revenda.payments.domain.model.Charge;
import java.time.Instant;

/** O que a compra mostra ao cliente: o código Pix e até quando vale. */
record CreatedChargeResponse(String chargeId, String pixCode, Instant dueAt) {

    static CreatedChargeResponse of(Charge charge) {
        return new CreatedChargeResponse(charge.id(), charge.pixCode(), charge.dueAt());
    }
}
