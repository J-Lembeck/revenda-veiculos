package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Customer;

public interface ReadPayerDataUseCase {

    /** O cadastro ativo para a cobrança, registrado na auditoria com o serviço e a compra; sem ele, {@code NOT_FOUND}. */
    Customer readPayer(ReadCustomerDataCommand command);
}
