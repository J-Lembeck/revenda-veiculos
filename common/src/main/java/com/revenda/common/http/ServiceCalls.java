package com.revenda.common.http;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientResponseException;

/** O que toda chamada a vizinho respeita: nenhuma sai com transação aberta, e o erro vai ao log sem o corpo. */
public final class ServiceCalls {

    private ServiceCalls() {
    }

    public static void requireNoTransaction(String neighbor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Chamada ao " + neighbor
                    + " com transação aberta: grave, faça o commit e só então chame");
        }
    }

    /** A classe da falha e, se houve resposta, o status — nunca o corpo nem a mensagem, que podem trazer dado. */
    public static String describe(RuntimeException failure) {
        if (failure instanceof RestClientResponseException response) {
            return failure.getClass().getSimpleName() + " " + response.getStatusCode().value();
        }
        return failure.getClass().getSimpleName();
    }
}
