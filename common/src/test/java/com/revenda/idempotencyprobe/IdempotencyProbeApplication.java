package com.revenda.idempotencyprobe;

import com.revenda.common.idempotency.IdempotencyStore;
import com.revenda.common.test.TestTokenConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@Import(TestTokenConfiguration.class)
class IdempotencyProbeApplication {

    @Bean
    AtomicInteger registrations() {
        return new AtomicInteger();
    }

    record VehicleRequest(String plate, long priceCents) {
    }

    record RegisteredVehicle(UUID id, String plate, long priceCents, Instant registeredAt) {
    }

    @RestController
    @RequiredArgsConstructor
    static class ProbeController {

        private final IdempotencyStore idempotency;
        private final TransactionTemplate transaction;
        private final AtomicInteger registrations;
        private final Clock clock;

        @PostMapping("/vehicles")
        ResponseEntity<RegisteredVehicle> register(@RequestBody VehicleRequest request) {
            IdempotencyStore.Outcome<RegisteredVehicle> outcome = transaction.execute(status ->
                    idempotency.once(RegisteredVehicle.class, () -> {
                        registrations.incrementAndGet();
                        return new RegisteredVehicle(UUID.randomUUID(), request.plate(), request.priceCents(),
                                clock.instant());
                    }));
            return ResponseEntity.status(HttpStatus.CREATED).body(outcome.result());
        }
    }
}
