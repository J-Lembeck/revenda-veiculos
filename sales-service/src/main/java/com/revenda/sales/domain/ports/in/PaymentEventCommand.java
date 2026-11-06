package com.revenda.sales.domain.ports.in;

import com.revenda.common.money.Money;
import com.revenda.sales.domain.model.PaymentEventType;
import java.time.Instant;
import java.util.Objects;

/** O aviso do gateway, já com a assinatura conferida: só ids, o valor e o instante. */
public record PaymentEventCommand(String eventId, PaymentEventType type, String chargeId, String referenceId,
        Money amount, Instant occurredAt) {

    public PaymentEventCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(chargeId, "chargeId");
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
