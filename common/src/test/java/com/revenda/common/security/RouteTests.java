package com.revenda.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RouteTests {

    @Test
    void internalRouteOnlyByServiceScope() {
        assertThatThrownBy(() -> Route.open(HttpMethod.GET, "/internal/customers/{id}/payer"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Route.forRoles(HttpMethod.GET, "/internal/customers/{id}/payer", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Route.forScope(HttpMethod.GET, "/internal/customers/{id}/payer", "customers.read-payer").scope())
                .isEqualTo("customers.read-payer");
    }

    @Test
    void scopeOnlyOnInternalRoute() {
        assertThatThrownBy(() -> Route.forScope(HttpMethod.POST, "/purchases", "vehicles.reserve"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
