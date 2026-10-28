package com.revenda.payments.domain.model;

/** O endereço do pagador; o complemento é opcional. */
public record PayerAddress(
        String zipCode,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state) {

    @Override
    public String toString() {
        return "PayerAddress[dados pessoais omitidos]";
    }
}
