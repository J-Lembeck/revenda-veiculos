package com.revenda.customers.domain.ports.out;

import com.revenda.customers.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;

public interface LoadCustomerPort {

    /** O cadastro ativo com esse id, já decifrado. */
    Optional<Customer> findActive(UUID id);

    /** Se a conta já tem cadastro ativo. */
    boolean existsActiveFor(String identitySubject);
}
