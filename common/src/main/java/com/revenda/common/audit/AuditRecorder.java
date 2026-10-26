package com.revenda.common.audit;

public interface AuditRecorder {

    /**
     * Grava na transação da ação, com o ator da requisição. Recusa fora de transação e com ator anônimo:
     * quem age numa rota pública declara antes o ator ({@code ActorContext.asSystem()}).
     */
    void record(AuditEntry entry);
}
