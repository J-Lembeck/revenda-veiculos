package com.revenda.sales.infrastructure.adapters.in.job;

import com.revenda.sales.domain.ports.in.SweepPurchasesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A varredura dos prazos da saga, a cada {@code SAGA_SWEEP_INTERVAL}: expira o que venceu e recupera o que parou. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "revenda.sales.jobs-enabled", havingValue = "true")
class PurchaseSweepJob {

    private final SweepPurchasesUseCase sweep;

    @Scheduled(fixedDelayString = "${revenda.sales.sweep-interval}")
    void sweep() {
        int expired = sweep.expireOverdue();
        int recovered = sweep.recoverStalled();
        if (expired + recovered > 0) {
            log.info("Varredura da saga: {} compras expiradas, {} recuperadas", expired, recovered);
        }
    }
}
