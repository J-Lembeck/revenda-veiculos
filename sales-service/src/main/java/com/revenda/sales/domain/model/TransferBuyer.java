package com.revenda.sales.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * O comprador como o documento de transferência pede, lido do cadastro na hora de emitir. Fica só em memória: vai para
 * o PDF e para mais lugar nenhum — nem banco, nem log, nem resposta.
 */
public record TransferBuyer(UUID customerId, String fullName, String cpf, String email, Payer.Address address) {

    public TransferBuyer {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(cpf, "cpf");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(address, "address");
    }

    @Override
    public String toString() {
        return "TransferBuyer[customerId=" + customerId + ", dados pessoais omitidos]";
    }
}
