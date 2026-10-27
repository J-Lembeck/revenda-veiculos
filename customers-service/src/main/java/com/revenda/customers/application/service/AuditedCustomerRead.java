package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.ReadCustomerDataCommand;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A leitura do dado completo do cliente por outro serviço: grava a auditoria com a compra na transação de quem chama,
 * sem retrato. Sem cadastro ativo, {@code NOT_FOUND} e nada gravado.
 */
@Component
@RequiredArgsConstructor
class AuditedCustomerRead {

    private final LoadCustomerPort customers;
    private final AuditRecorder audit;

    Customer read(ReadCustomerDataCommand command, CustomersAuditAction action, String summary) {
        Customer customer = customers.findActive(command.customerId()).orElseThrow(BusinessException::notFound);
        audit.record(AuditEntry.of(action, Customer.ENTITY_TYPE, customer.id(),
                        summary + " " + command.purchaseId())
                .withExtra(new PurchaseReference(command.purchaseId())));
        return customer;
    }
}
