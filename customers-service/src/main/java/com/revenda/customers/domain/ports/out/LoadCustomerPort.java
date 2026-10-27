package com.revenda.customers.domain.ports.out;

import com.revenda.customers.domain.model.Cpf;
import com.revenda.customers.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;

public interface LoadCustomerPort {

    /** O cadastro ativo com esse id, já decifrado. */
    Optional<Customer> findActive(UUID id);

    /** O cadastro ativo da conta, já decifrado. */
    Optional<Customer> findActiveFor(String identitySubject);

    /** Se a conta já tem cadastro ativo. */
    boolean existsActiveFor(String identitySubject);

    /**
     * Se o CPF bate com o do cadastro ativo com esse id, pelo índice cego e sem decifrar nada; vazio se não há cadastro
     * ativo com esse id.
     */
    Optional<Boolean> cpfMatches(UUID id, Cpf cpf);
}
