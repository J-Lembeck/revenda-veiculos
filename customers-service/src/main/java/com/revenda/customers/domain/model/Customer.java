package com.revenda.customers.domain.model;

import java.time.Instant;
import java.util.UUID;

/** O cadastro do comprador, ligado a uma conta do provedor de identidade pelo {@code identitySubject}. */
public record Customer(
        UUID id,
        String identitySubject,
        EmailAddress email,
        CustomerDetails details,
        CustomerStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static final String ENTITY_TYPE = "CUSTOMER";

    /** Cadastro novo: ativo, na versão 0. */
    public static Customer register(UUID id, String identitySubject, EmailAddress email, CustomerDetails details,
            Instant now) {
        return new Customer(id, identitySubject, email, details, CustomerStatus.ACTIVE, 0, now, now);
    }

    @Override
    public String toString() {
        return "Customer[id=" + id + ", status=" + status + "]";
    }
}
