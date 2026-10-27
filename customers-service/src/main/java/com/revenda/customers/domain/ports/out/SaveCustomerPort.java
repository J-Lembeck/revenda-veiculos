package com.revenda.customers.domain.ports.out;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerField;
import java.util.Set;

public interface SaveCustomerPort {

    /**
     * Grava o cadastro novo, com o dado pessoal cifrado; a conta ou o CPF que já têm cadastro ativo saem como
     * {@code CUSTOMER_ALREADY_REGISTERED} ou {@code CPF_ALREADY_REGISTERED}.
     */
    void insert(Customer customer);

    /**
     * Grava a versão editada do cadastro, que veio da versão anterior travada: só os campos {@code changed} são
     * cifrados e gravados de novo; o resto da linha fica como está.
     */
    void update(Customer edited, Set<CustomerField> changed);
}
