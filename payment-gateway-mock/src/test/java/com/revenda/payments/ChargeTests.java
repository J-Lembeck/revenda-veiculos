package com.revenda.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.test.SyntheticData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** As cobranças do gateway: criar uma por referência, achar pela referência, cancelar e estornar sem nunca falhar. */
@ExtendWith(OutputCaptureExtension.class)
class ChargeTests extends PaymentGatewayApiTest {

    @Test
    void aNewChargeIsPendingWithTheSyntheticPixCodeAndThePayerNeverShows(CapturedOutput output) {
        Map<String, Object> body = chargeBody();
        String cpf = cpfOf(body);

        ResponseEntity<JsonNode> created = api().post(CHARGES, body);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode answer = created.getBody();
        assertThat(answer.propertyNames()).containsExactlyInAnyOrder("chargeId", "pixCode", "dueAt");
        assertThat(answer.get("chargeId").asString()).matches("chg_[0-9a-f]{32}");
        assertThat(answer.get("pixCode").asString()).startsWith("000201010212").contains("pix@example.test",
                "540845900.00", "REVENDA SIMULADA");
        assertThat(Instant.parse(answer.get("dueAt").asString())).isEqualTo(Instant.parse((String) body.get("dueAt")));
        JsonNode stored = chargeOf(body);
        assertThat(stored.propertyNames()).containsExactlyInAnyOrder("chargeId", "referenceId", "amountCents",
                "paidAmountCents", "pixCode", "dueAt", "status", "createdAt", "updatedAt");
        assertThat(stored.get("chargeId").asString()).isEqualTo(answer.get("chargeId").asString());
        assertThat(stored.get("referenceId").asString()).isEqualTo(body.get("referenceId"));
        assertThat(stored.get("amountCents").asLong()).isEqualTo(AMOUNT_CENTS);
        assertThat(stored.get("paidAmountCents").isNull()).isTrue();
        assertThat(stored.get("pixCode").asString()).isEqualTo(answer.get("pixCode").asString());
        assertThat(stored.get("status").asString()).isEqualTo("PENDING");
        assertThat(answer.toString() + stored).doesNotContain(cpf, PAYER_NAME, STREET, "01310000");
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), PAYER_NAME, STREET, "01310000");
    }

    @Test
    void theSameReferenceWithTheSameAmountIsTheSameChargeAndAnotherAmountIsAConflict() {
        Map<String, Object> body = chargeBody();
        String chargeId = create(body);
        Map<String, Object> repeated = new HashMap<>(body);
        repeated.put("dueAt", Instant.now().plus(Duration.ofHours(2)).toString());
        Map<String, Object> otherAmount = new HashMap<>(body);
        otherAmount.put("amountCents", AMOUNT_CENTS + 1);

        ResponseEntity<JsonNode> again = api().post(CHARGES, repeated);
        ResponseEntity<JsonNode> conflict = api().post(CHARGES, otherAmount);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().get("chargeId").asString()).isEqualTo(chargeId);
        assertThat(again.getBody().get("dueAt").asString()).isEqualTo(chargeOf(body).get("dueAt").asString());
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("code").asString()).isEqualTo("CHARGE_REFERENCE_CONFLICT");
        assertThat(chargeOf(body).get("amountCents").asLong()).isEqualTo(AMOUNT_CENTS);
    }

    @Test
    void manyRequestsForTheSameReferenceAtOnceLeaveOneCharge() throws Exception {
        Map<String, Object> body = chargeBody();
        List<Callable<ResponseEntity<JsonNode>>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> api().post(CHARGES, body));
        }

        List<ResponseEntity<JsonNode>> answers = atOnce(calls);

        assertThat(answers).extracting(answer -> answer.getBody().get("chargeId").asString()).containsOnly(
                chargeOf(body).get("chargeId").asString());
        assertThat(answers).filteredOn(answer -> answer.getStatusCode() == HttpStatus.CREATED).hasSize(1);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                arguments("referenceId", null, "referenceId REQUIRED"),
                arguments("referenceId", "r".repeat(65), "referenceId SIZE_INVALID"),
                arguments("amountCents", null, "amountCents REQUIRED"),
                arguments("amountCents", 0, "amountCents OUT_OF_RANGE"),
                arguments("amountCents", "muito", "amountCents TYPE_INVALID"),
                arguments("dueAt", null, "dueAt REQUIRED"),
                arguments("dueAt", "2020-01-01T00:00:00Z", "dueAt DATE_INVALID"),
                arguments("payer", null, "payer REQUIRED"),
                arguments("payer.name", " ", "payer.name REQUIRED"),
                arguments("payer.cpf", "529.982.247-25", "payer.cpf FORMAT_INVALID"),
                arguments("payer.address.zipCode", "01310-000", "payer.address.zipCode FORMAT_INVALID"),
                arguments("payer.address.state", "sp", "payer.address.state FORMAT_INVALID"),
                arguments("payer.address.street", null, "payer.address.street REQUIRED"));
    }

    @ParameterizedTest(name = "{0} = {1} → {2}")
    @MethodSource("invalidRequests")
    void anInvalidRequestCreatesNothing(String field, Object value, String expected) {
        Map<String, Object> changes = new HashMap<>();
        changes.put(field, value);
        Map<String, Object> body = chargeBody(changes);

        ResponseEntity<JsonNode> response = api().post(CHARGES, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldsOf(response)).containsExactly(expected);
        if (body.get("referenceId") instanceof String reference && reference.length() <= 64) {
            assertThat(api().get(CHARGES + "?referenceId=" + reference).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void theSearchNeedsAReferenceAndAnUnknownOneIsNotFound() {
        ResponseEntity<JsonNode> missing = api().get(CHARGES);
        ResponseEntity<JsonNode> unknown = api().get(CHARGES + "?referenceId=referencia-que-nao-existe");

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(missing)).containsExactly("referenceId REQUIRED");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void cancellingAPendingChargeCancelsItAndCancellingAgainIsNotAnError() {
        Map<String, Object> body = chargeBody();
        String chargeId = create(body);

        ResponseEntity<JsonNode> first = api().post(pathOf(chargeId, "cancel"), null);
        ResponseEntity<JsonNode> again = api().post(pathOf(chargeId, "cancel"), null);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody()).isEqualTo(first.getBody());
        assertThat(chargeOf(body).get("status").asString()).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAPaidChargeChangesNothing() {
        Map<String, Object> body = chargeBody();
        String chargeId = create(body);
        pay(chargeId);

        ResponseEntity<JsonNode> response = api().post(pathOf(chargeId, "cancel"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asString()).isEqualTo("PAID");
    }

    @Test
    void refundingAPaidChargeRefundsItOnceAndSendsOneRefundEvent() {
        Map<String, Object> body = chargeBody();
        String chargeId = create(body);
        pay(chargeId);

        ResponseEntity<JsonNode> first = api().post(pathOf(chargeId, "refund"), null);
        ResponseEntity<JsonNode> again = api().post(pathOf(chargeId, "refund"), null);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("status").asString()).isEqualTo("REFUNDED");
        assertThat(first.getBody().get("paidAmountCents").asLong()).isEqualTo(AMOUNT_CENTS);
        assertThat(again.getBody()).isEqualTo(first.getBody());
        await().atMost(Duration.ofSeconds(5)).until(() -> refundsOf(chargeId).size() == 1);
        await().during(Duration.ofMillis(400)).atMost(Duration.ofSeconds(2)).until(() -> refundsOf(chargeId).size() == 1);
        JsonNode refund = refundsOf(chargeId).get(0);
        assertThat(refund.get("referenceId").asString()).isEqualTo(body.get("referenceId"));
        assertThat(refund.get("amountCents").asLong()).isEqualTo(AMOUNT_CENTS);
    }

    @Test
    void refundingAChargeThatWasNotPaidChangesNothingAndSendsNothing() {
        Map<String, Object> body = chargeBody();
        String pending = create(body);
        String cancelled = create(chargeBody());
        api().post(pathOf(cancelled, "cancel"), null);

        ResponseEntity<JsonNode> notPaid = api().post(pathOf(pending, "refund"), null);
        ResponseEntity<JsonNode> notPaidCancelled = api().post(pathOf(cancelled, "refund"), null);

        assertThat(notPaid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notPaid.getBody().get("status").asString()).isEqualTo("PENDING");
        assertThat(notPaidCancelled.getBody().get("status").asString()).isEqualTo("CANCELLED");
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .until(() -> refundsOf(pending).isEmpty() && refundsOf(cancelled).isEmpty());
    }

    @Test
    void cancellingOrRefundingAChargeThatDoesNotExistIsNotFound() {
        for (String action : List.of("cancel", "refund", "simulate")) {
            ResponseEntity<JsonNode> response = api().post(pathOf("chg_inexistente", action),
                    action.equals("simulate") ? Map.of("outcome", "PAID") : null);
            assertThat(response.getStatusCode()).as(action).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        }
    }

    @Test
    void everyRouteButTheHealthNeedsTheApiKey() {
        Map<String, Object> body = chargeBody();

        ResponseEntity<JsonNode> withoutKey = anonymous().post(CHARGES, body);
        ResponseEntity<JsonNode> wrongKey = anonymous().withHeader("X-Api-Key", "chave-errada").post(CHARGES, body);
        ResponseEntity<JsonNode> search = anonymous().get(CHARGES + "?referenceId=" + body.get("referenceId"));
        ResponseEntity<String> health = anonymous().exchange(HttpMethod.GET, "/actuator/health", null, String.class);

        for (ResponseEntity<JsonNode> refused : List.of(withoutKey, wrongKey, search)) {
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(refused.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
            assertThat(refused.getBody().get("message").asString()).contains("X-Api-Key");
            assertThat(refused.getBody().get("requestId").asString())
                    .isEqualTo(refused.getHeaders().getFirst("X-Request-Id"));
        }
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api().get(CHARGES + "?referenceId=" + body.get("referenceId")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void pay(String chargeId) {
        assertThat(api().post(pathOf(chargeId, "simulate"), Map.of("outcome", "PAID")).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
    }

    private List<JsonNode> refundsOf(String chargeId) {
        return eventsOf(chargeId, "CHARGE_REFUNDED");
    }

    @SuppressWarnings("unchecked")
    private static String cpfOf(Map<String, Object> body) {
        return (String) ((Map<String, Object>) body.get("payer")).get("cpf");
    }

    private static List<ResponseEntity<JsonNode>> atOnce(List<Callable<ResponseEntity<JsonNode>>> calls) throws Exception {
        CyclicBarrier start = new CyclicBarrier(calls.size());
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (Callable<ResponseEntity<JsonNode>> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return call.call();
                }));
            }
            List<ResponseEntity<JsonNode>> answers = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> future : futures) {
                answers.add(future.get(30, TimeUnit.SECONDS));
            }
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }
}
