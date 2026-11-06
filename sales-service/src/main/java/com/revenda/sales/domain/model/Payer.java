package com.revenda.sales.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem paga a cobrança, lido do cadastro na hora de emitir e passado ao gateway. Fica só em memória: o {@code sales}
 * não grava, não registra no log nem devolve.
 */
public record Payer(UUID customerId, String fullName, String cpf, Address address) {

    public Payer {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(cpf, "cpf");
        Objects.requireNonNull(address, "address");
    }

    public record Address(String zipCode, String street, String number, String complement, String district,
            String city, String state) {

        @Override
        public String toString() {
            return "Address[dados pessoais omitidos]";
        }
    }

    @Override
    public String toString() {
        return "Payer[customerId=" + customerId + ", dados pessoais omitidos]";
    }
}
