package com.revenda.common.idempotency;

import java.util.function.Supplier;

public interface IdempotencyStore {

    /**
     * Roda o comando uma vez por ator e chave, dentro da transação já aberta: a chave e o resultado são
     * gravados com o efeito, e a repetição devolve o resultado guardado sem rodar de novo. {@code POST} sem
     * chave é recusado com {@code IDEMPOTENCY_KEY_REQUIRED}; a mesma chave com outro pedido, com
     * {@code IDEMPOTENCY_KEY_REUSED}. O resultado guardado nunca leva dado pessoal: guarde o id e refaça a
     * resposta.
     */
    <T> Outcome<T> once(Class<T> resultType, Supplier<T> command);

    /** Apaga as chaves mais velhas que a retenção e devolve quantas apagou. */
    int purgeExpired();

    record Outcome<T>(T result, boolean replayed) {
    }
}
