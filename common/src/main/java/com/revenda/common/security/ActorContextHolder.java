package com.revenda.common.security;

import java.util.function.Supplier;

public final class ActorContextHolder {

    private static final ThreadLocal<ActorContext> CONTEXT = new ThreadLocal<>();

    private ActorContextHolder() {
    }

    public static void set(ActorContext context) {
        CONTEXT.set(context);
    }

    /** Fora de uma requisição (job, consumidor de fila) o ator é o sistema. */
    public static ActorContext get() {
        ActorContext context = CONTEXT.get();
        return context != null ? context : ActorContext.system();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /** Roda o trabalho com outro ator e devolve o de antes ao terminar, mesmo se falhar. */
    public static <T> T callAs(ActorContext context, Supplier<T> work) {
        ActorContext previous = CONTEXT.get();
        CONTEXT.set(context);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}
