package com.revenda.common.audit;

/** Implementada pelo enum de ações de cada serviço; o nome da constante é o que vai para a coluna. */
public interface AuditAction {

    String name();
}
