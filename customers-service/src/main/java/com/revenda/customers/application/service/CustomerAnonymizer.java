package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.jdbc.Transactions;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.out.SaveCustomerPrivacyPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Anonimiza o cadastro já travado, na transação de quem chama: grava {@code CUSTOMER_ANONYMIZED} e, depois do
 * commit, soma um em {@code customers.anonymizations} com o gatilho ({@code request} ou {@code retention}).
 */
@Component
@RequiredArgsConstructor
class CustomerAnonymizer {

    static final String METRIC = "customers.anonymizations";

    private final SaveCustomerPrivacyPort privacy;
    private final AuditRecorder audit;
    private final MeterRegistry meters;

    boolean anonymize(UUID customerId, Instant at, String summary, String trigger) {
        if (!privacy.anonymize(customerId, at)) {
            return false;
        }
        audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_ANONYMIZED, Customer.ENTITY_TYPE, customerId, summary));
        Transactions.afterCommit(() -> meters.counter(METRIC, "trigger", trigger).increment());
        return true;
    }
}
