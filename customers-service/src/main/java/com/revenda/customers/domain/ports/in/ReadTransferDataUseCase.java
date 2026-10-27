package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Customer;

public interface ReadTransferDataUseCase {

    /**
     * O cadastro ativo para o documento de transferência, registrado na auditoria com o serviço e a compra; sem ele,
     * {@code NOT_FOUND}.
     */
    Customer readTransferData(ReadCustomerDataCommand command);
}
