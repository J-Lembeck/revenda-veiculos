package com.revenda.common.security;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpMethod;

/**
 * Quem pode chamar uma rota: qualquer um, uma pessoa com um dos papéis ou um serviço com o scope. Rota em
 * {@code /internal/} só aceita serviço, e scope só existe em rota interna.
 */
public record Route(HttpMethod method, String pattern, Set<Role> roles, String scope) {

    private static final String INTERNAL_PREFIX = "/internal/";

    public Route {
        roles = Set.copyOf(roles);
        if (pattern.startsWith(INTERNAL_PREFIX) != (scope != null)) {
            throw new IllegalArgumentException(
                    "Rota interna só por scope de serviço, e scope só em rota interna: " + pattern);
        }
    }

    public static Route open(String pattern) {
        return new Route(null, pattern, Set.of(), null);
    }

    public static Route open(HttpMethod method, String pattern) {
        return new Route(method, pattern, Set.of(), null);
    }

    public static Route forRoles(HttpMethod method, String pattern, Role first, Role... others) {
        return new Route(method, pattern, EnumSet.of(first, others), null);
    }

    public static Route forScope(HttpMethod method, String pattern, String scope) {
        return new Route(method, pattern, Set.of(), scope);
    }
}
