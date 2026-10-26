package com.revenda.idempotencyprobe;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import com.revenda.idempotencyprobe.IdempotencyProbeApplication.VehicleRequest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ApiDriverTests extends IdempotencyProbeSupport {

    private static final VehicleRequest VEHICLE = new VehicleRequest("ABC1D23", 8990000);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AtomicInteger registrations;

    @Test
    void postsJsonWithTheCallerTokenAndAFreshKeyEachTime() {
        ApiDriver seller = new ApiDriver(rest).asSeller("4a2f9c10-vendedora");
        int before = registrations.get();

        ResponseEntity<JsonNode> first = seller.post("/vehicles", VEHICLE);
        ResponseEntity<JsonNode> second = seller.post("/vehicles", VEHICLE);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("plate").asString()).isEqualTo("ABC1D23");
        assertThat(first.getBody().get("priceCents").asLong()).isEqualTo(8990000);
        assertThat(second.getBody().get("id")).isNotEqualTo(first.getBody().get("id"));
        assertThat(registrations).hasValue(before + 2);
    }

    @Test
    void fixedKeyRepeatsTheSameRequest() {
        ApiDriver seller = new ApiDriver(rest).asSeller("4a2f9c10-vendedora").withIdempotencyKey(UUID.randomUUID().toString());
        int before = registrations.get();

        ResponseEntity<JsonNode> first = seller.post("/vehicles", VEHICLE);
        ResponseEntity<JsonNode> second = seller.post("/vehicles", VEHICLE);

        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(registrations).hasValue(before + 1);
    }

    @Test
    void withoutKeyTheCreationIsRefused() {
        ResponseEntity<JsonNode> response =
                new ApiDriver(rest).asSeller("4a2f9c10-vendedora").withoutIdempotencyKey().post("/vehicles", VEHICLE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void anonymousCallCarriesNoToken() {
        ResponseEntity<JsonNode> response = new ApiDriver(rest).asSeller("4a2f9c10-vendedora").anonymous()
                .post("/vehicles", VEHICLE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }
}
