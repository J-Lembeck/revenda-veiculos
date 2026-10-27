package com.revenda.customers.domain.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
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

    /**
     * Com o que o titular mudou e o e-mail da conta; o nulo fica como está. Sem mudança nenhuma, volta o próprio
     * cadastro; com alguma, a versão seguinte, atualizada em {@code now}.
     */
    public Customer edit(String fullName, Phone phone, Address address, EmailAddress accountEmail, Instant now) {
        CustomerDetails editedDetails = details.edit(fullName, phone, address);
        EmailAddress editedEmail = accountEmail == null ? email : accountEmail;
        if (editedDetails.equals(details) && editedEmail.equals(email)) {
            return this;
        }
        return new Customer(id, identitySubject, editedEmail, editedDetails, status, version + 1, createdAt, now);
    }

    /** O que muda deste cadastro para o {@code edited}. */
    public Set<CustomerField> changesTo(Customer edited) {
        Set<CustomerField> changed = EnumSet.noneOf(CustomerField.class);
        if (!details.fullName().equals(edited.details.fullName())) {
            changed.add(CustomerField.FULL_NAME);
        }
        if (!details.phone().equals(edited.details.phone())) {
            changed.add(CustomerField.PHONE);
        }
        if (!details.address().equals(edited.details.address())) {
            changed.add(CustomerField.ADDRESS);
        }
        if (!email.equals(edited.email)) {
            changed.add(CustomerField.EMAIL);
        }
        return changed;
    }

    @Override
    public String toString() {
        return "Customer[id=" + id + ", status=" + status + "]";
    }
}
