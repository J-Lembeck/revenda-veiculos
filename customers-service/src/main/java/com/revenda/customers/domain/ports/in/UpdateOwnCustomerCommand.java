package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Address;

/**
 * O que a pessoa da conta {@code identitySubject} muda no próprio cadastro: nome, telefone e endereço inteiro, já nos
 * formatos do cadastro; o nulo fica como está. O e-mail e se ele foi verificado vêm do token.
 */
public record UpdateOwnCustomerCommand(
        String identitySubject,
        String email,
        boolean emailVerified,
        String fullName,
        String phone,
        Address address) {

    @Override
    public String toString() {
        return "UpdateOwnCustomerCommand[dados pessoais omitidos]";
    }
}
