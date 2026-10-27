package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Customer;

public interface UpdateOwnCustomerUseCase {

    Customer update(UpdateOwnCustomerCommand command);
}
