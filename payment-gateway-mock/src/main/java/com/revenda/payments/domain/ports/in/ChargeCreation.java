package com.revenda.payments.domain.ports.in;

import com.revenda.payments.domain.model.Charge;

/** A cobrança do pedido e se ela nasceu agora ou já existia para a referência (a repetição). */
public record ChargeCreation(Charge charge, boolean created) {
}
