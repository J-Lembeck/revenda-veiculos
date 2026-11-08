package com.revenda.sales.infrastructure.config;

import com.revenda.common.security.RateLimitRule;
import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
class SalesSecurityConfiguration {

    @Bean
    SecurityRoutes salesRoutes() {
        return new SecurityRoutes() {
            @Override
            public List<Route> routes() {
                return List.of(
                        Route.forRoles(HttpMethod.POST, "/purchases", Role.CUSTOMER),
                        Route.forRoles(HttpMethod.GET, "/purchases", Role.CUSTOMER, Role.SELLER),
                        Route.forRoles(HttpMethod.GET, "/purchases/{id}", Role.CUSTOMER, Role.SELLER),
                        Route.forRoles(HttpMethod.POST, "/purchases/{id}/cancellation", Role.CUSTOMER),
                        Route.forRoles(HttpMethod.POST, "/purchases/{id}/pickup", Role.SELLER),
                        Route.forRoles(HttpMethod.GET, "/purchases/{id}/document", Role.CUSTOMER, Role.SELLER),
                        Route.open(HttpMethod.POST, "/webhooks/payments"));
            }

            @Override
            public List<RateLimitRule> rateLimits() {
                return List.of(new RateLimitRule(HttpMethod.POST, "/purchases", 10, Duration.ofMinutes(1)));
            }
        };
    }
}
