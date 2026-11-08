package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerExport;
import com.revenda.customers.domain.ports.in.ExportOwnDataUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import com.revenda.customers.domain.ports.out.LoadCustomerPrivacyPort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O histórico vem antes da auditoria da própria exportação, que só aparece na exportação seguinte. */
@Service
@RequiredArgsConstructor
public class ExportOwnDataService implements ExportOwnDataUseCase {

    private final LoadCustomerPort customers;
    private final LoadCustomerPrivacyPort privacy;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public CustomerExport export(String identitySubject) {
        Customer customer = customers.findActiveFor(identitySubject).orElseThrow(BusinessException::notFound);
        CustomerExport export = new CustomerExport(customer, privacy.deletionRequestedAt(customer.id()).orElse(null),
                privacy.historyOf(customer.id(), identitySubject), clock.instant());
        audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_EXPORTED, Customer.ENTITY_TYPE, customer.id(),
                "Dados exportados pelo titular"));
        return export;
    }
}
