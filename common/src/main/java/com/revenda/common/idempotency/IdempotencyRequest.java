package com.revenda.common.idempotency;

/**
 * O que o filtro leu de um {@code POST}: a chave enviada pelo cliente, a rota e o resumo do pedido.
 * {@link #absent()} marca o {@code POST} que chegou sem a chave.
 */
public record IdempotencyRequest(String key, String scope, String requestHash) {

    private static final IdempotencyRequest ABSENT = new IdempotencyRequest(null, null, null);

    public static IdempotencyRequest absent() {
        return ABSENT;
    }

    public boolean isAbsent() {
        return key == null;
    }
}
