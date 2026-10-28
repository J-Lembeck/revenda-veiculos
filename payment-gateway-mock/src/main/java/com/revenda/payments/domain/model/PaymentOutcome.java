package com.revenda.payments.domain.model;

import com.revenda.common.money.Money;

/** O que a simulação faz o "cliente" pagar: o valor cobrado, ou R$ 1,00 a menos (no mínimo um centavo). */
public enum PaymentOutcome {
    PAID,
    PAID_WRONG_AMOUNT;

    static final Money WRONG_BY = Money.ofCents(100);
    static final Money LEAST = Money.ofCents(1);

    public Money paidFor(Money charged) {
        if (this == PAID) {
            return charged;
        }
        Money less = charged.minus(WRONG_BY);
        return less.isLessThan(LEAST) ? LEAST : less;
    }
}
