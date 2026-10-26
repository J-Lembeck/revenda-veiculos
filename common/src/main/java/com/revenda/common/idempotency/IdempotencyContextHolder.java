package com.revenda.common.idempotency;

public final class IdempotencyContextHolder {

    private static final ThreadLocal<IdempotencyRequest> CONTEXT = new ThreadLocal<>();

    private IdempotencyContextHolder() {
    }

    public static void set(IdempotencyRequest request) {
        CONTEXT.set(request);
    }

    /** Nulo fora de um {@code POST} HTTP: job e consumidor de fila não repetem pela chave. */
    public static IdempotencyRequest get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
