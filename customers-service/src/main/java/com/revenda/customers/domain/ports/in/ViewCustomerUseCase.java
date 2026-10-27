package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Customer;
import java.util.UUID;

public interface ViewCustomerUseCase {

    /** O cadastro ativo com esse id, para a equipe; sem ele, {@code NOT_FOUND}. */
    Customer view(UUID id);
}
