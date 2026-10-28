package com.revenda.payments.domain.model;

/** A cobrança antes e depois de um comando; iguais quando o comando não mudou nada. */
public record ChargeChange(Charge before, Charge after) {

    public boolean moved() {
        return before.status() != after.status();
    }
}
