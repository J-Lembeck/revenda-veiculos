package com.revenda.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class IdempotencyFilterTests {

    private static final String KEY = "6d1f4b2a-9c3e-4f5a-8b7d-0e2c1a3b4d5f";
    private static final String BODY = "{\"vehicleId\":\"0b4c2f6e-1a2b-4c3d-8e9f-001122334455\"}";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final IdempotencyFilter filter = new IdempotencyFilter(new ApiErrorWriter(objectMapper));

    @Test
    void postWithoutTheKeyGoesOnMarkedAbsent() throws Exception {
        Seen seen = call(post("/purchases", null, BODY));

        assertThat(seen.request()).isEqualTo(IdempotencyRequest.absent());
        assertThat(seen.body()).isEqualTo(BODY);
    }

    @Test
    void keyGoesOnWithTheRouteAndTheSummaryAndTheBodyStaysReadable() throws Exception {
        Seen seen = call(post("/purchases", KEY, BODY));

        assertThat(seen.request().key()).isEqualTo(KEY);
        assertThat(seen.request().scope()).isEqualTo("POST /purchases");
        assertThat(seen.request().requestHash()).hasSize(64);
        assertThat(seen.body()).isEqualTo(BODY);
    }

    @Test
    void idsInThePathBecomeAPlaceholderInTheScope() throws Exception {
        Seen seen = call(post("/purchases/0b4c2f6e-1a2b-4c3d-8e9f-001122334455/cancellation", KEY, "{}"));

        assertThat(seen.request().scope()).isEqualTo("POST /purchases/{id}/cancellation");
    }

    @Test
    void summaryChangesWithTheBodyThePathOrTheQueryAndOnlyWithThem() throws Exception {
        String original = call(post("/purchases", KEY, BODY)).request().requestHash();

        assertThat(call(post("/purchases", "outra-chave-0123456789", BODY)).request().requestHash()).isEqualTo(original);
        assertThat(call(post("/purchases", KEY, BODY.replace("0011", "9911"))).request().requestHash())
                .isNotEqualTo(original);
        assertThat(call(post("/customers", KEY, BODY)).request().requestHash()).isNotEqualTo(original);
        MockHttpServletRequest withQuery = post("/purchases", KEY, BODY);
        withQuery.setQueryString("dryRun=true");
        assertThat(call(withQuery).request().requestHash()).isNotEqualTo(original);
    }

    @Test
    void malformedKeyIsRefusedOnTheHeaderWithoutGoingOn() throws Exception {
        for (String key : List.of("", "curta", "x".repeat(81), "chave com espaço 0123456789", "chave/0123456789abc")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<Boolean> wentOn = new AtomicReference<>(false);

            filter.doFilter(post("/purchases", key, BODY), response, (request, ignored) -> wentOn.set(true));

            assertThat(wentOn.get()).as(key).isFalse();
            assertThat(response.getStatus()).isEqualTo(400);
            Map<?, ?> error = objectMapper.readValue(response.getContentAsString(StandardCharsets.UTF_8), Map.class);
            assertThat(error.get("code")).isEqualTo("VALIDATION_FAILED");
            assertThat(error.get("fields")).isEqualTo(List.of(Map.of(
                    "field", "Idempotency-Key",
                    "code", "FORMAT_INVALID",
                    "message", IdempotencyFilter.KEY_INVALID_MESSAGE)));
        }
    }

    @Test
    void bodyAboveTheLimitIsRefused() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = post("/purchases", KEY, null);
        request.setContent(new byte[IdempotencyFilter.BODY_MAX_BYTES + 1]);

        filter.doFilter(request, response, (ignored, alsoIgnored) -> {
            throw new AssertionError("não devia seguir");
        });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(objectMapper.readValue(response.getContentAsString(StandardCharsets.UTF_8), Map.class))
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("message", IdempotencyFilter.BODY_TOO_LARGE_MESSAGE);
    }

    @Test
    void otherMethodsAreNotRead() throws Exception {
        for (String method : List.of("GET", "PUT", "PATCH", "DELETE")) {
            MockHttpServletRequest request = post("/vehicles/0b4c2f6e-1a2b-4c3d-8e9f-001122334455", KEY, BODY);
            request.setMethod(method);

            assertThat(call(request).request()).as(method).isNull();
        }
    }

    @Test
    void contextIsClearedAfterTheRequestEvenWhenItFails() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (request, ignored) -> {
            throw new IllegalStateException("o caso de uso falhou");
        };

        assertThatThrownBy(() -> filter.doFilter(post("/purchases", KEY, BODY), response, failing))
                .hasMessage("o caso de uso falhou");

        assertThat(IdempotencyContextHolder.get()).isNull();
    }

    private Seen call(MockHttpServletRequest request) throws Exception {
        AtomicReference<Seen> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (inner, ignored) -> seen.set(new Seen(
                IdempotencyContextHolder.get(), new String(inner.getInputStream().readAllBytes(), StandardCharsets.UTF_8))));
        assertThat(IdempotencyContextHolder.get()).isNull();
        return seen.get();
    }

    private static MockHttpServletRequest post(String path, String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (key != null) {
            request.addHeader(IdempotencyFilter.HEADER, key);
        }
        if (body != null) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        request.setContentType("application/json");
        return request;
    }

    private record Seen(IdempotencyRequest request, String body) {
    }
}
