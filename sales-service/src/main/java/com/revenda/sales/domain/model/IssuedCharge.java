package com.revenda.sales.domain.model;

import java.time.Instant;

/** A cobrança que o gateway emitiu para a compra: o id dela, o código Pix e até quando vale. */
public record IssuedCharge(String chargeId, String pixCode, Instant dueAt) {

    @Override
    public String toString() {
        return "IssuedCharge[chargeId=" + chargeId + ", dueAt=" + dueAt + "]";
    }
}
