package com.revenda.sales.domain.ports.in;

/**
 * UC-09 (RN-12): aplica o aviso do gateway à compra, uma vez por {@code eventId}. O evento que chega antes de a
 * compra estar aguardando o pagamento sai como {@code PAYMENT_EVENT_NOT_YET_APPLICABLE}, e o que contradiz a compra,
 * como {@code PAYMENT_EVENT_CONTRADICTORY} — nos dois, nada fica gravado e o gateway entrega de novo.
 */
public interface ReceivePaymentEventUseCase {

    Outcome receive(PaymentEventCommand event);

    enum Outcome {
        APPLIED,
        DUPLICATE,
        NO_EFFECT
    }
}
