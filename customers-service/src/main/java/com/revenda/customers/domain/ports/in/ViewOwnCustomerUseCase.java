package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Customer;

public interface ViewOwnCustomerUseCase {

    /** O cadastro ativo da conta {@code identitySubject}; sem cadastro, {@code NOT_FOUND}. */
    Customer view(String identitySubject);
}
