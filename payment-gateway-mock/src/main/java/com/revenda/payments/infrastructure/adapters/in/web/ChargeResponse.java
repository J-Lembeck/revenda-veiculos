package com.revenda.payments.infrastructure.adapters.in.web;

import com.revenda.common.money.Money;
import com.revenda.payments.domain.model.Charge;
import com.revenda.payments.domain.model.ChargeStatus;
import java.time.Instant;

/** A cobrança como o {@code sales} a vê: sem o pagador. {@code paidAmountCents} só depois do pagamento. */
record ChargeResponse(
        String chargeId,
        String referenceId,
        long amountCents,
        Long paidAmountCents,
        String pixCode,
        Instant dueAt,
        ChargeStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static ChargeResponse of(Charge charge) {
        Money paid = charge.paidAmount();
        return new ChargeResponse(charge.id(), charge.referenceId(), charge.amount().toCents(),
                paid == null ? null : paid.toCents(), charge.pixCode(), charge.dueAt(), charge.status(),
                charge.createdAt(), charge.updatedAt());
    }
}
