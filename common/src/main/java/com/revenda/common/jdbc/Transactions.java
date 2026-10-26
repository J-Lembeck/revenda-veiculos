package com.revenda.common.jdbc;

import javax.sql.DataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class Transactions {

    private Transactions() {
    }

    public static void requireOpen(DataSource dataSource, String refusal) {
        if (!TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalTransactionStateException(refusal);
        }
    }
}
