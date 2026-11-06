package com.revenda.sales.application.service;

import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.ports.in.SweepPurchasesUseCase;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada compra numa transação curta: trava a próxima da fila ({@code FOR UPDATE SKIP LOCKED}), confere de novo no
 * domínio e grava a mudança com as compensações no outbox. A que o webhook ou a desistência travou fica para a passada
 * seguinte, que a encontra já mudada; vários contêineres varrem juntos sem pegar a mesma.
 */
@Service
@RequiredArgsConstructor
public class SweepPurchasesService implements SweepPurchasesUseCase {

    static final int BATCH_LIMIT = 100;

    private final LockPurchasePort locks;
    private final SavePurchasePort savedPurchases;
    private final SagaOutbox outbox;
    private final TransactionTemplate transaction;
    private final SalesProperties properties;
    private final Clock clock;

    @Override
    public int expireOverdue() {
        return sweep(now -> locks.lockNextOverdue(now).flatMap(purchase -> purchase.expire(now)));
    }

    @Override
    public int recoverStalled() {
        return sweep(now -> locks.lockNextStalled(now.minus(properties.recoveryThreshold()))
                .flatMap(purchase -> purchase.recover(properties.recoveryThreshold(), now)));
    }

    private int sweep(Function<Instant, Optional<PurchaseChange>> next) {
        int changed = 0;
        while (changed < BATCH_LIMIT && Boolean.TRUE.equals(transaction.execute(status -> {
            Optional<PurchaseChange> change = next.apply(clock.instant().truncatedTo(ChronoUnit.MICROS));
            change.ifPresent(this::apply);
            return change.isPresent();
        }))) {
            changed++;
        }
        return changed;
    }

    private void apply(PurchaseChange change) {
        savedPurchases.update(change);
        outbox.publishAfter(change);
    }
}
