package com.revenda.customers.application.service;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.ReadCustomerDataCommand;
import com.revenda.customers.domain.ports.in.ReadPayerDataUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadPayerDataService implements ReadPayerDataUseCase {

    private final AuditedCustomerRead read;

    @Override
    @Transactional
    public Customer readPayer(ReadCustomerDataCommand command) {
        return read.read(command, CustomersAuditAction.CUSTOMER_PAYER_DATA_READ, "Dados do pagador lidos para a compra");
    }
}
