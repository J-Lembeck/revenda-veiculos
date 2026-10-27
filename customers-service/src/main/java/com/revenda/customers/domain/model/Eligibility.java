package com.revenda.customers.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Se a conta pode comprar (RN-05): cadastro ativo e 18 anos ou mais na data da compra. Sem cadastro, não há
 * {@code customerId}; elegível, não há {@code reason}.
 */
public record Eligibility(UUID customerId, boolean eligible, IneligibilityReason reason) {

    public static Eligibility missingProfile() {
        return new Eligibility(null, false, IneligibilityReason.PROFILE_MISSING);
    }

    public static Eligibility of(Customer customer, LocalDate purchaseDate) {
        return customer.details().isAdultOn(purchaseDate)
                ? new Eligibility(customer.id(), true, null)
                : new Eligibility(customer.id(), false, IneligibilityReason.UNDERAGE);
    }
}
