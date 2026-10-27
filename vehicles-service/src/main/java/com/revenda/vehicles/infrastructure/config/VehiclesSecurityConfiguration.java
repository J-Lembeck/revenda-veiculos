package com.revenda.vehicles.infrastructure.config;

import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
class VehiclesSecurityConfiguration {

    @Bean
    SecurityRoutes vehiclesRoutes() {
        return () -> List.of(
                Route.forRoles(HttpMethod.POST, "/vehicles", Role.SELLER),
                Route.forRoles(HttpMethod.PUT, "/vehicles/{id}", Role.SELLER),
                Route.forRoles(HttpMethod.GET, "/vehicles/sold", Role.SELLER),
                Route.open(HttpMethod.GET, "/vehicles"),
                Route.open(HttpMethod.GET, "/vehicles/{id}"));
    }
}
