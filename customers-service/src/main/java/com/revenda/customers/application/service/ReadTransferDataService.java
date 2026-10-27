package com.revenda.customers.application.service;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.ReadCustomerDataCommand;
import com.revenda.customers.domain.ports.in.ReadTransferDataUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadTransferDataService implements ReadTransferDataUseCase {

    private final AuditedCustomerRead read;

    @Override
    @Transactional
    public Customer readTransferData(ReadCustomerDataCommand command) {
        return read.read(command, CustomersAuditAction.CUSTOMER_TRANSFER_DATA_READ,
                "Dados do documento de transferência lidos para a compra");
    }
}
