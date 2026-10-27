package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.customers.domain.model.Cpf;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.RevealCpfCommand;
import com.revenda.customers.domain.ports.in.RevealCpfUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada revelação grava {@code CUSTOMER_CPF_REVEALED} com o motivo e soma um em {@code customers.cpf.reveals}, a
 * métrica do alarme de volume; quem revelou fica na auditoria, nunca na métrica.
 */
@Service
@RequiredArgsConstructor
public class RevealCpfService implements RevealCpfUseCase {

    static final String REVEALS_METRIC = "customers.cpf.reveals";

    private final LoadCustomerPort customers;
    private final AuditRecorder audit;
    private final MeterRegistry meters;

    @Override
    @Transactional
    public Cpf reveal(RevealCpfCommand command) {
        Customer customer = customers.findActive(command.customerId()).orElseThrow(BusinessException::notFound);
        audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_CPF_REVEALED, Customer.ENTITY_TYPE, customer.id(),
                        "CPF completo revelado")
                .withReason(command.reason()));
        meters.counter(REVEALS_METRIC).increment();
        return customer.details().cpf();
    }
}
