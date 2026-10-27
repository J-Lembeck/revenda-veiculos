package com.revenda.customers.infrastructure.config;

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
class CustomersSecurityConfiguration {

    @Bean
    SecurityRoutes customersRoutes() {
        return new SecurityRoutes() {
            @Override
            public List<Route> routes() {
                return List.of(
                        Route.forRoles(HttpMethod.POST, "/customers", Role.CUSTOMER),
                        Route.forRoles(HttpMethod.GET, "/customers/me", Role.CUSTOMER),
                        Route.forRoles(HttpMethod.PATCH, "/customers/me", Role.CUSTOMER),
                        Route.forRoles(HttpMethod.GET, "/customers/{id}", Role.SELLER),
                        Route.forRoles(HttpMethod.POST, "/customers/{id}/cpf-reveal", Role.ADMIN));
            }

            @Override
            public List<RateLimitRule> rateLimits() {
                return List.of(new RateLimitRule(HttpMethod.POST, "/customers", 5, Duration.ofMinutes(1)));
            }
        };
    }
}
