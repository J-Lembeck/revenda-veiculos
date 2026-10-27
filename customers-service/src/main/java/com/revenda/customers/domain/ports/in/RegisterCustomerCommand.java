package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Address;
import java.time.LocalDate;

/**
 * O cadastro pedido pela pessoa da conta {@code identitySubject}. O e-mail e se ele foi verificado vêm do token; o
 * resto, do corpo, ainda sem a conferência do CPF e da data de nascimento.
 */
public record RegisterCustomerCommand(
        String identitySubject,
        String email,
        boolean emailVerified,
        String fullName,
        String cpf,
        LocalDate birthDate,
        String phone,
        Address address) {

    @Override
    public String toString() {
        return "RegisterCustomerCommand[dados pessoais omitidos]";
    }
}
