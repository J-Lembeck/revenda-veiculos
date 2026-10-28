package com.revenda.payments.infrastructure.adapters.in.web;

import com.revenda.payments.domain.model.PaymentEvent;
import com.revenda.payments.domain.model.PaymentEventType;

/** O evento que o webhook vai levar e em quantas entregas; a entrega é depois da resposta. */
record SimulationResponse(String eventId, PaymentEventType type, long amountCents, int deliveries) {

    static SimulationResponse of(PaymentEvent event, int deliveries) {
        return new SimulationResponse(event.id(), event.type(), event.amount().toCents(), deliveries);
    }
}
