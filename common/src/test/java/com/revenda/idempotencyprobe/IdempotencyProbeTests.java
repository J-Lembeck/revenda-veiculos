package com.revenda.idempotencyprobe;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.error.FieldViolation;
import com.revenda.common.error.web.ApiError;
import com.revenda.common.idempotency.IdempotencyFilter;
import com.revenda.common.observability.RequestIdFilter;
import com.revenda.common.test.ApiDriver;
import com.revenda.idempotencyprobe.IdempotencyProbeApplication.RegisteredVehicle;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IdempotencyProbeTests extends IdempotencyProbeSupport {

    private static final String SELLER = "3c8e1f20-vendedora";
    private static final String OTHER_SELLER = "7b4d9a61-vendedor";
    private static final String BODY = "{\"plate\":\"ABC1D23\",\"priceCents\":8990000}";
    private static final String OTHER_BODY = "{\"plate\":\"ABC1D23\",\"priceCents\":9000000}";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AtomicInteger registrations;

    @Test
    void creationWithoutTheKeyAnswersPreconditionRequiredAndRunsNothing() {
        int before = registrations.get();

        ResponseEntity<ApiError> response = post(seller(SELLER).withoutIdempotencyKey(), BODY, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(response.getBody().requestId()).isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
        assertThat(registrations).hasValue(before);
    }

    @Test
    void repetitionWithTheSameKeyAndBodyGetsTheSameResponse() {
        ApiDriver seller = seller(SELLER).withIdempotencyKey(newKey());
        int before = registrations.get();

        ResponseEntity<RegisteredVehicle> first = post(seller, BODY, RegisteredVehicle.class);
        ResponseEntity<RegisteredVehicle> second = post(seller, BODY, RegisteredVehicle.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(registrations).hasValue(before + 1);
    }

    @Test
    void sameKeyWithAnotherBodyAnswersUnprocessable() {
        ApiDriver seller = seller(SELLER).withIdempotencyKey(newKey());
        post(seller, BODY, RegisteredVehicle.class);

        ResponseEntity<ApiError> response = post(seller, OTHER_BODY, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(response.getBody().message())
                .isEqualTo("Esta Idempotency-Key já foi usada com outro conteúdo. Gere uma chave nova para um pedido novo.");
    }

    @Test
    void theSameKeyFromAnotherPersonIsAnotherRequest() {
        String key = newKey();

        ResponseEntity<RegisteredVehicle> mine =
                post(seller(SELLER).withIdempotencyKey(key), BODY, RegisteredVehicle.class);
        ResponseEntity<RegisteredVehicle> theirs =
                post(seller(OTHER_SELLER).withIdempotencyKey(key), BODY, RegisteredVehicle.class);

        assertThat(theirs.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(theirs.getBody().id()).isNotEqualTo(mine.getBody().id());
    }

    @Test
    void malformedKeyAnswersValidationFailedOnTheHeader() {
        int before = registrations.get();

        ResponseEntity<ApiError> response = post(seller(SELLER).withIdempotencyKey("curta"), BODY, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fields()).containsExactly(new FieldViolation(IdempotencyFilter.HEADER,
                "FORMAT_INVALID", "Use um UUID: de 16 a 80 letras, números, hífen ou sublinhado."));
        assertThat(response.getBody().requestId()).isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
        assertThat(registrations).hasValue(before);
    }

    @Test
    void requestWithoutTokenIsRefusedBeforeTheKeyIsLookedAt() {
        ResponseEntity<ApiError> response = post(new ApiDriver(rest).withIdempotencyKey("curta"), BODY, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    private ApiDriver seller(String subject) {
        return new ApiDriver(rest).asSeller(subject);
    }

    private static <T> ResponseEntity<T> post(ApiDriver driver, String rawBody, Class<T> type) {
        return driver.exchange(HttpMethod.POST, "/vehicles", rawBody, type);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
