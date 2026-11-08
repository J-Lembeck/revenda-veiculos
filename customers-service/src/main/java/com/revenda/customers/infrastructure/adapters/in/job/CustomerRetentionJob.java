package com.revenda.customers.infrastructure.adapters.in.job;

import com.revenda.customers.domain.ports.in.AnonymizeDueCustomersUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** O job de retenção, em {@code CUSTOMERS_RETENTION_CRON} (todo dia às 3h, no fuso da revenda, por padrão). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "revenda.customers.jobs-enabled", havingValue = "true")
class CustomerRetentionJob {

    private final AnonymizeDueCustomersUseCase anonymizeDue;

    @Scheduled(cron = "${revenda.customers.retention-cron}", zone = "${revenda.customers.zone}")
    void anonymizeDue() {
        int anonymized = anonymizeDue.anonymizeDue();
        if (anonymized > 0) {
            log.info("Retenção: {} cadastros anonimizados no fim do prazo de guarda", anonymized);
        }
    }
}
