package com.revenda.payments.domain.model;

import com.revenda.common.money.Money;
import java.time.Instant;

/** O aviso que vai ao webhook do {@code sales}: só ids, o valor e o instante, nunca o pagador. */
public record PaymentEvent(String id, PaymentEventType type, String chargeId, String referenceId, Money amount,
        Instant occurredAt) {

    /** O pagamento da cobrança, com o valor que foi pago. */
    public static PaymentEvent paid(String id, Charge charge) {
        return new PaymentEvent(id, PaymentEventType.CHARGE_PAID, charge.id(), charge.referenceId(), charge.paidAmount(),
                charge.updatedAt());
    }

    /** O estorno da cobrança, do valor que foi pago. */
    public static PaymentEvent refunded(String id, Charge charge) {
        return new PaymentEvent(id, PaymentEventType.CHARGE_REFUNDED, charge.id(), charge.referenceId(),
                charge.paidAmount(), charge.updatedAt());
    }
}
