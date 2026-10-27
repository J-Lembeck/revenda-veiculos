package com.revenda.customers.domain.model;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.FieldViolation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** O que o comprador informa de si no cadastro; o e-mail vem da conta, à parte. */
public record CustomerDetails(String fullName, Cpf cpf, LocalDate birthDate, Phone phone, Address address) {

    public static final int FULL_NAME_LIMIT = 150;
    public static final LocalDate FIRST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    /**
     * Confere, de uma vez, o CPF (RN-06) e a data de nascimento, de 1900 até ontem; os formatos dos outros campos
     * já chegam conferidos. A maioridade não é exigida aqui: ela vale na compra (RN-05).
     */
    public static CustomerDetails of(String fullName, String cpf, LocalDate birthDate, String phone, Address address,
            LocalDate today) {
        List<FieldViolation> violations = new ArrayList<>();
        Optional<Cpf> parsedCpf = Cpf.parse(cpf);
        if (parsedCpf.isEmpty()) {
            violations.add(new FieldViolation("cpf", "CPF_INVALID",
                    "O CPF tem 11 dígitos e os dois últimos conferem os anteriores. Confira o número."));
        }
        if (birthDate == null || birthDate.isBefore(FIRST_BIRTH_DATE) || !birthDate.isBefore(today)) {
            violations.add(new FieldViolation("birthDate", "DATE_INVALID",
                    "A data de nascimento vai de 01/01/1900 até ontem."));
        }
        if (!violations.isEmpty()) {
            throw BusinessException.invalid(violations.toArray(FieldViolation[]::new));
        }
        return new CustomerDetails(fullName.strip(), parsedCpf.orElseThrow(), birthDate, new Phone(phone), address);
    }

    /** Com o nome, o telefone e o endereço novos; o nulo fica como está, e CPF e nascimento não mudam (RN-07). */
    public CustomerDetails edit(String newFullName, Phone newPhone, Address newAddress) {
        return new CustomerDetails(newFullName == null ? fullName : newFullName.strip(), cpf, birthDate,
                newPhone == null ? phone : newPhone, newAddress == null ? address : newAddress);
    }

    @Override
    public String toString() {
        return "CustomerDetails[dados pessoais omitidos]";
    }
}
