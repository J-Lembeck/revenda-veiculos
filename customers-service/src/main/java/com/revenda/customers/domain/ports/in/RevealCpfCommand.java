package com.revenda.customers.domain.ports.in;

import java.util.UUID;

/** O pedido do CPF completo do cliente {@code customerId}, com o motivo escrito por quem pede. */
public record RevealCpfCommand(UUID customerId, String reason) {

    public static final int REASON_MIN = 10;
    public static final int REASON_MAX = 300;
}
