package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.MovableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * A resposta do cadastro e da edição mostra o veículo como o estoque guardou. O relógio tem nanossegundos e o banco
 * guarda microssegundos: o instante montado em memória não é o que o {@code GET} mostra depois.
 */
class VehicleAnswerTimeTests extends VehiclesApiTest {

    private static final Instant WITH_NANOSECONDS = Instant.parse("2026-10-27T12:00:00.123456789Z");

    @Autowired
    private MovableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockWithNanoseconds {

        @Bean
        MovableClock clockWithNanoseconds() {
            return new MovableClock(WITH_NANOSECONDS);
        }
    }

    @Test
    void theRegistrationAndItsRepetitionAnswerTheVehicleAsStored() {
        ApiDriver seller = api().asSeller(SELLER).withIdempotencyKey(UUID.randomUUID().toString());
        Map<String, Object> body = vehicleBody();

        ResponseEntity<JsonNode> first = seller.post("/vehicles", body);
        ResponseEntity<JsonNode> repeated = seller.post("/vehicles", body);

        JsonNode stored = api().asSeller(SELLER).get("/vehicles/" + first.getBody().get("id").asString()).getBody();
        assertThat(first.getBody()).isEqualTo(stored);
        assertThat(repeated.getBody()).isEqualTo(stored);
        assertThat(stored.get("createdAt").asString()).endsWith(".123457Z");
    }

    @Test
    void theEditAnswersTheVehicleAsStored() {
        Registered vehicle = register();
        clock.advance(Duration.ofSeconds(1));

        ResponseEntity<JsonNode> response = api().asSeller(SELLER)
                .put(vehicle.path(), vehicle.edit(Map.of("color", "Grafite")));

        assertThat(response.getBody()).isEqualTo(api().asSeller(SELLER).get(vehicle.path()).getBody());
    }
}
