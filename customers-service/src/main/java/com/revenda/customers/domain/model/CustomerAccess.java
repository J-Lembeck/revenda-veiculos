package com.revenda.customers.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Um registro da auditoria sobre o cadastro, como o titular o vê: o que aconteceu, quando, quem fez em termos gerais
 * (o próprio titular, alguém da equipe com o papel, um serviço pelo nome do cliente ou o sistema) e, se houve, o
 * motivo e a compra. Nunca o {@code sub} de quem fez.
 */
public record CustomerAccess(
        Instant at,
        String action,
        Actor actor,
        String role,
        String service,
        String summary,
        String reason,
        UUID purchaseId) {

    public enum Actor {
        OWNER,
        STAFF,
        SERVICE,
        SYSTEM
    }
}
