package com.revenda.customers.domain.ports.in;

import java.util.UUID;

/** O CPF apresentado na retirada da compra {@code purchaseId}, como foi digitado, para conferir com o cadastro. */
public record CheckCpfCommand(UUID customerId, UUID purchaseId, String cpf) {

    @Override
    public String toString() {
        return "CheckCpfCommand[customerId=" + customerId + ", purchaseId=" + purchaseId + "]";
    }
}
