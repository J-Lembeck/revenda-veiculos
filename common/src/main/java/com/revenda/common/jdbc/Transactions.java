package com.revenda.common.jdbc;

import javax.sql.DataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class Transactions {

    private Transactions() {
    }

    public static void requireOpen(DataSource dataSource, String refusal) {
        if (!TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalTransactionStateException(refusal);
        }
    }

    /**
     * Roda depois do commit da transação aberta, e nunca se ela for desfeita; sem transação, roda já. Para o que não
     * pode contar uma mudança que não aconteceu, como uma métrica.
     */
    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
