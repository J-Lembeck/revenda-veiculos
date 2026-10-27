package com.revenda.customers.application.service;

import com.revenda.customers.domain.model.Customer;

/** O retrato do cadastro na auditoria: só o que não é dado pessoal. */
record CustomerSnapshot(String status, int version) {

    static CustomerSnapshot of(Customer customer) {
        return new CustomerSnapshot(customer.status().name(), customer.version());
    }
}
