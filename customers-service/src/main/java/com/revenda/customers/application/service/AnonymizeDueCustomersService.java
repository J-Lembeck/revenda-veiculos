package com.revenda.customers.application.service;

import com.revenda.common.error.DependencyUnavailableException;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.customers.domain.ports.in.AnonymizeDueCustomersUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPrivacyPort;
import com.revenda.customers.domain.ports.out.LoadRetentionPort;
import com.revenda.customers.domain.ports.out.LockCustomerPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada pedido de exclusão é conferido de novo com o {@code sales}, sem transação aberta: uma compra cancelada encurta
 * o prazo, uma venda nova o estende. O que venceu é anonimizado como {@code SYSTEM}, com a linha travada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymizeDueCustomersService implements AnonymizeDueCustomersUseCase {

    private final LoadCustomerPrivacyPort privacy;
    private final LockCustomerPort locks;
    private final LoadRetentionPort retention;
    private final CustomerAnonymizer anonymizer;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Override
    public int anonymizeDue() {
        return ActorContextHolder.callAs(ActorContext.system(), this::anonymizeAsSystem);
    }

    private int anonymizeAsSystem() {
        List<UUID> requested = privacy.deletionRequested();
        int anonymized = 0;
        for (UUID id : requested) {
            Optional<Instant> retainUntil;
            try {
                retainUntil = retention.retainUntil(id);
            } catch (DependencyUnavailableException unavailable) {
                log.warn("Retenção sem resposta do serviço de vendas ({}); o resto fica para a próxima passada",
                        unavailable.getMessage());
                break;
            }
            Instant now = clock.instant();
            if (retainUntil.isPresent() && retainUntil.get().isAfter(now)) {
                continue;
            }
            if (Boolean.TRUE.equals(transaction.execute(status -> locks.lockActive(id).isPresent()
                    && anonymizer.anonymize(id, now, "Cadastro anonimizado no fim do prazo de guarda", "retention")))) {
                anonymized++;
            }
        }
        return anonymized;
    }
}
