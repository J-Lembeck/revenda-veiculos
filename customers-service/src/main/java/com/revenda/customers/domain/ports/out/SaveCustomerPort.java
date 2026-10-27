package com.revenda.customers.domain.ports.out;

import com.revenda.customers.domain.model.Customer;

public interface SaveCustomerPort {

    /**
     * Grava o cadastro novo, com o dado pessoal cifrado; a conta ou o CPF que já têm cadastro ativo saem como
     * {@code CUSTOMER_ALREADY_REGISTERED} ou {@code CPF_ALREADY_REGISTERED}.
     */
    void insert(Customer customer);
}
