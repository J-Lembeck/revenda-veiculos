package com.revenda.common.security;

import java.util.List;

/**
 * As regras de acesso de um serviço, declaradas num bean dele. Rota fora da lista exige só um token válido;
 * rota em {@code /internal/} fora da lista é negada.
 */
public interface SecurityRoutes {

    List<Route> routes();

    default List<RateLimitRule> rateLimits() {
        return List.of();
    }
}
