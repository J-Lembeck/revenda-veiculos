package com.revenda.customers.domain.ports.out;

import com.revenda.customers.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;

public interface LockCustomerPort {

    /** O cadastro ativo da conta, já decifrado, com a linha travada até o fim da transação. */
    Optional<Customer> lockActiveFor(String identitySubject);

    /** O cadastro ativo com esse id, já decifrado, com a linha travada até o fim da transação. */
    Optional<Customer> lockActive(UUID id);
}
