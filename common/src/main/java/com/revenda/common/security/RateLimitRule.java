package com.revenda.common.security;

import java.time.Duration;
import org.springframework.http.HttpMethod;

/** Quantas chamadas a uma rota cabem na janela, contadas por rede de origem. A cota volta inteira ao fim dela. */
public record RateLimitRule(HttpMethod method, String pattern, long capacity, Duration window) {

    public RateLimitRule {
        if (method == null || capacity < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException(
                    "Limite de taxa precisa de método, capacidade e janela positivas: " + pattern);
        }
    }

    String key() {
        return method.name() + ' ' + pattern;
    }
}
