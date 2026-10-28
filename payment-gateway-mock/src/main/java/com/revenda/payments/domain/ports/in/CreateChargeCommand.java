package com.revenda.payments.domain.ports.in;

import com.revenda.common.money.Money;
import com.revenda.payments.domain.model.Payer;
import java.time.Instant;

/** O pedido de cobrança de uma compra ({@code referenceId}), com o valor, o vencimento e o pagador. */
public record CreateChargeCommand(String referenceId, Money amount, Instant dueAt, Payer payer) {

    public static final int REFERENCE_LIMIT = 64;

    @Override
    public String toString() {
        return "CreateChargeCommand[referenceId=" + referenceId + ", amount=" + amount + "]";
    }
}
