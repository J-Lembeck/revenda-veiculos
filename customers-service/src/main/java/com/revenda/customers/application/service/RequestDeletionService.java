package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.common.error.CommonError;
import com.revenda.common.error.DependencyUnavailableException;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomersError;
import com.revenda.customers.domain.ports.in.RequestDeletionUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import com.revenda.customers.domain.ports.out.LoadRetentionPort;
import com.revenda.customers.domain.ports.out.LockCustomerPort;
import com.revenda.customers.domain.ports.out.SaveCustomerPrivacyPort;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pergunta ao {@code sales} até quando guardar — sem transação aberta — e só então trava o cadastro: anonimiza na hora
 * ou registra o pedido para o job de retenção. O pedido agendado responde {@code CUSTOMER_HAS_RETAINED_DATA} depois
 * do commit, com a data no fuso da revenda. Sem o {@code sales}, {@code DEPENDENCY_UNAVAILABLE} e nada muda.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestDeletionService implements RequestDeletionUseCase {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LoadCustomerPort customers;
    private final LockCustomerPort locks;
    private final SaveCustomerPrivacyPort privacy;
    private final LoadRetentionPort retention;
    private final CustomerAnonymizer anonymizer;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;
    private final CustomersProperties properties;
    private final Clock clock;

    @Override
    public Instant request(String identitySubject) {
        UUID id = customers.findActiveFor(identitySubject).map(Customer::id).orElseThrow(BusinessException::notFound);
        Optional<Instant> retainUntil = retainUntil(id);
        Instant now = clock.instant();
        if (retainUntil.isPresent() && retainUntil.get().isAfter(now)) {
            transaction.executeWithoutResult(status -> {
                lock(identitySubject, id);
                if (privacy.requestDeletion(id, now)) {
                    audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_DELETION_REQUESTED, Customer.ENTITY_TYPE,
                                    id, "Exclusão pedida pelo titular; agendada para o fim do prazo de guarda")
                            .withExtra(new RetentionEnd(retainUntil.get())));
                }
            });
            throw BusinessException.of(CustomersError.CUSTOMER_HAS_RETAINED_DATA, scheduledMessage(retainUntil.get()));
        }
        transaction.executeWithoutResult(status -> {
            lock(identitySubject, id);
            privacy.requestDeletion(id, now);
            audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_DELETION_REQUESTED, Customer.ENTITY_TYPE, id,
                    "Exclusão pedida pelo titular, sem venda no prazo de guarda"));
            anonymizer.anonymize(id, now, "Cadastro anonimizado a pedido do titular", "request");
        });
        return now;
    }

    private Optional<Instant> retainUntil(UUID id) {
        try {
            return retention.retainUntil(id);
        } catch (DependencyUnavailableException unavailable) {
            log.warn("Pedido de exclusão sem resposta do serviço de vendas: {}", unavailable.getMessage());
            throw BusinessException.of(CommonError.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void lock(String identitySubject, UUID id) {
        locks.lockActiveFor(identitySubject).filter(customer -> customer.id().equals(id))
                .orElseThrow(BusinessException::notFound);
    }

    private String scheduledMessage(Instant retainUntil) {
        return "Há venda no prazo em que a lei pede guardar os dados: o pedido de exclusão ficou registrado e o "
                + "cadastro é anonimizado em " + DATE.format(retainUntil.atZone(properties.zone()))
                + ", no fim desse prazo.";
    }

    /** O fim do prazo de guarda, como vai na auditoria do pedido agendado. */
    record RetentionEnd(Instant retainUntil) {
    }
}
