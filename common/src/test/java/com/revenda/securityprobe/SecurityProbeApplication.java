package com.revenda.securityprobe;

import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.RateLimitRule;
import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import com.revenda.common.test.TestTokenConfiguration;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@Import(TestTokenConfiguration.class)
class SecurityProbeApplication {

    static final int PURCHASE_LIMIT = 3;

    @Bean
    SecurityRoutes probeRoutes() {
        return new SecurityRoutes() {

            @Override
            public List<Route> routes() {
                return List.of(
                        Route.open(HttpMethod.GET, "/vehicles"),
                        Route.open(HttpMethod.GET, "/{area}/vehicles/{id}"),
                        Route.forRoles(HttpMethod.POST, "/purchases", Role.CUSTOMER),
                        Route.forRoles(HttpMethod.GET, "/customers/me", Role.CUSTOMER),
                        Route.forRoles(HttpMethod.GET, "/purchases", Role.CUSTOMER, Role.SELLER),
                        Route.forRoles(HttpMethod.GET, "/vehicles/sold", Role.SELLER),
                        Route.forRoles(HttpMethod.POST, "/customers/{id}/cpf-reveal", Role.ADMIN),
                        Route.forRoles(HttpMethod.GET, "/denied-inside", Role.CUSTOMER),
                        Route.forScope(HttpMethod.POST, "/internal/vehicles/{id}/reservations", "vehicles.reserve"));
            }

            @Override
            public List<RateLimitRule> rateLimits() {
                return List.of(new RateLimitRule(HttpMethod.POST, "/purchases", PURCHASE_LIMIT, Duration.ofMinutes(1)));
            }
        };
    }

    @RestController
    static class ProbeController {

        @GetMapping("/vehicles")
        ActorContext showcase() {
            return ActorContextHolder.get();
        }

        @PostMapping("/purchases")
        ActorContext purchase() {
            return ActorContextHolder.get();
        }

        @GetMapping("/customers/me")
        ActorContext ownProfile() {
            return ActorContextHolder.get();
        }

        @GetMapping("/purchases")
        ActorContext purchases() {
            return ActorContextHolder.get();
        }

        @GetMapping("/vehicles/sold")
        ActorContext sold() {
            return ActorContextHolder.get();
        }

        @PostMapping("/customers/{id}/cpf-reveal")
        ActorContext revealCpf() {
            return ActorContextHolder.get();
        }

        @PostMapping("/internal/vehicles/{id}/reservations")
        ActorContext reserve() {
            return ActorContextHolder.get();
        }

        @GetMapping("/session")
        ActorContext session() {
            return ActorContextHolder.get();
        }

        @GetMapping("/denied-inside")
        void deniedInside() {
            throw new AccessDeniedException("negado dentro do caso de uso");
        }
    }
}
