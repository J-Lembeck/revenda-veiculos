package com.revenda.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.test.SyntheticData;
import com.revenda.common.webhook.WebhookSignature;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A simulação do pagamento: o gateway marca a cobrança paga e manda o webhook assinado ao {@code sales}, em segundo
 * plano, reentregando enquanto não recebe {@code 2xx}.
 */
@ExtendWith(OutputCaptureExtension.class)
class PaymentSimulationTests extends PaymentGatewayApiTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final WebhookSignature signature = new WebhookSignature(WEBHOOK_SECRET);

    @AfterEach
    void answerTwoHundredAgain() {
        receiver().answer();
    }

    @Test
    void aPaymentAnswersAcceptedAndTheSignedWebhookCarriesOnlyIdsAmountAndTime(CapturedOutput output) {
        Map<String, Object> body = chargeBody();
        String cpf = (String) ((Map<?, ?>) body.get("payer")).get("cpf");
        String chargeId = create(body);

        ResponseEntity<JsonNode> response = simulate(chargeId, Map.of("outcome", "PAID"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode simulation = response.getBody();
        assertThat(simulation.propertyNames()).containsExactlyInAnyOrder("eventId", "type", "amountCents", "deliveries");
        String eventId = simulation.get("eventId").asString();
        assertThat(eventId).matches("evt_[0-9a-f]{32}");
        assertThat(simulation.get("type").asString()).isEqualTo("CHARGE_PAID");
        assertThat(simulation.get("amountCents").asLong()).isEqualTo(AMOUNT_CENTS);
        assertThat(simulation.get("deliveries").asInt()).isEqualTo(1);
        WebhookReceiver.Delivery delivery = awaitDeliveries(eventId, 1).get(0);
        assertThat(delivery.contentType()).isEqualTo("application/json");
        assertThat(signature.verify(delivery.signature(), delivery.body(), Instant.now(), WINDOW)).isTrue();
        JsonNode event = eventOf(delivery);
        assertThat(event.propertyNames()).containsExactlyInAnyOrder("eventId", "type", "chargeId", "referenceId",
                "amountCents", "occurredAt");
        assertThat(event.get("eventId").asString()).isEqualTo(eventId);
        assertThat(event.get("type").asString()).isEqualTo("CHARGE_PAID");
        assertThat(event.get("chargeId").asString()).isEqualTo(chargeId);
        assertThat(event.get("referenceId").asString()).isEqualTo(body.get("referenceId"));
        assertThat(event.get("amountCents").asLong()).isEqualTo(AMOUNT_CENTS);
        assertThat(Instant.parse(event.get("occurredAt").asString()))
                .isEqualTo(Instant.parse(chargeOf(body).get("updatedAt").asString()));
        assertThat(delivery.text()).doesNotContain(cpf, PAYER_NAME, STREET);
        JsonNode charge = chargeOf(body);
        assertThat(charge.get("status").asString()).isEqualTo("PAID");
        assertThat(charge.get("paidAmountCents").asLong()).isEqualTo(AMOUNT_CENTS);
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), PAYER_NAME, STREET, WEBHOOK_SECRET,
                API_KEY);
    }

    @Test
    void theWrongAmountIsOneRealLessInTheWebhookAndInTheCharge() {
        Map<String, Object> body = chargeBody();
        String chargeId = create(body);

        String eventId = simulate(chargeId, Map.of("outcome", "PAID_WRONG_AMOUNT")).getBody().get("eventId").asString();

        JsonNode event = eventOf(awaitDeliveries(eventId, 1).get(0));
        assertThat(event.get("amountCents").asLong()).isEqualTo(AMOUNT_CENTS - 100);
        assertThat(chargeOf(body).get("paidAmountCents").asLong()).isEqualTo(AMOUNT_CENTS - 100);
        assertThat(chargeOf(body).get("amountCents").asLong()).isEqualTo(AMOUNT_CENTS);
    }

    @Test
    void twoDeliveriesSendTheSameEventTwice() {
        String chargeId = create(chargeBody());

        ResponseEntity<JsonNode> response = simulate(chargeId, Map.of("outcome", "PAID", "deliveries", 2));

        String eventId = response.getBody().get("eventId").asString();
        assertThat(response.getBody().get("deliveries").asInt()).isEqualTo(2);
        List<WebhookReceiver.Delivery> deliveries = awaitDeliveries(eventId, 2);
        assertThat(deliveries.get(0).text()).isEqualTo(deliveries.get(1).text());
        await().during(Duration.ofMillis(300)).atMost(WAIT).until(() -> receiver().deliveriesOf(eventId).size() == 2);
    }

    @Test
    void aCancelledChargeIsStillPaidBecauseThePixArrivedLate() {
        Map<String, Object> body = chargeBody();
        String chargeId = create(body);
        api().post(pathOf(chargeId, "cancel"), null);

        ResponseEntity<JsonNode> response = simulate(chargeId, Map.of("outcome", "PAID"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitDeliveries(response.getBody().get("eventId").asString(), 1);
        assertThat(chargeOf(body).get("status").asString()).isEqualTo("PAID");
    }

    @Test
    void aPaidOrRefundedChargeIsNotPaidAgainAndNothingIsSent() {
        String paid = create(chargeBody());
        awaitDeliveries(simulate(paid, Map.of("outcome", "PAID")).getBody().get("eventId").asString(), 1);
        String refunded = create(chargeBody());
        awaitDeliveries(simulate(refunded, Map.of("outcome", "PAID")).getBody().get("eventId").asString(), 1);
        api().post(pathOf(refunded, "refund"), null);

        for (String chargeId : List.of(paid, refunded)) {
            ResponseEntity<JsonNode> again = simulate(chargeId, Map.of("outcome", "PAID"));
            assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(again.getBody().get("code").asString()).isEqualTo("CHARGE_NOT_PAYABLE");
        }
        await().during(Duration.ofMillis(300)).atMost(WAIT)
                .until(() -> eventsOf(paid, "CHARGE_PAID").size() == 1 && eventsOf(refunded, "CHARGE_PAID").size() == 1);
    }

    static Stream<Arguments> invalidSimulations() {
        return Stream.of(
                arguments(null, 1, "outcome REQUIRED"),
                arguments("PAGO", 1, "outcome TYPE_INVALID"),
                arguments("PAID", 0, "deliveries OUT_OF_RANGE"),
                arguments("PAID", 4, "deliveries OUT_OF_RANGE"));
    }

    @ParameterizedTest(name = "outcome {0}, deliveries {1} → {2}")
    @MethodSource("invalidSimulations")
    void anInvalidSimulationPaysNothing(String outcome, Integer deliveries, String expected) {
        Map<String, Object> body = chargeBody();
        String chargeId = create(body);
        Map<String, Object> simulation = new HashMap<>();
        simulation.put("outcome", outcome);
        simulation.put("deliveries", deliveries);

        ResponseEntity<JsonNode> response = simulate(chargeId, simulation);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactly(expected);
        assertThat(chargeOf(body).get("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void theWebhookIsDeliveredAgainUntilTheSalesAnswersTwoHundredEachTimeFreshlySigned() {
        String chargeId = create(chargeBody());
        receiver().answer(500, 503);

        String eventId = simulate(chargeId, Map.of("outcome", "PAID")).getBody().get("eventId").asString();

        List<WebhookReceiver.Delivery> deliveries = awaitDeliveries(eventId, 3);
        await().during(Duration.ofMillis(400)).atMost(WAIT).until(() -> receiver().deliveriesOf(eventId).size() == 3);
        for (WebhookReceiver.Delivery delivery : deliveries) {
            assertThat(signature.verify(delivery.signature(), delivery.body(), Instant.now(), WINDOW)).isTrue();
        }
        assertThat(Duration.between(deliveries.get(0).receivedAt(), deliveries.get(2).receivedAt()))
                .isGreaterThanOrEqualTo(Duration.ofMillis(150));
    }

    @Test
    void afterThreeRedeliveriesTheGatewayGivesUp(CapturedOutput output) {
        String chargeId = create(chargeBody());
        receiver().answer(500, 500, 500, 500, 500);

        String eventId = simulate(chargeId, Map.of("outcome", "PAID")).getBody().get("eventId").asString();

        awaitDeliveries(eventId, 4);
        await().during(Duration.ofMillis(500)).atMost(WAIT).until(() -> receiver().deliveriesOf(eventId).size() == 4);
        assertThat(output).contains("Webhook " + eventId + " não entregue depois de 4 tentativas");
    }

    private ResponseEntity<JsonNode> simulate(String chargeId, Map<String, Object> simulation) {
        return api().post(pathOf(chargeId, "simulate"), simulation);
    }

    private List<WebhookReceiver.Delivery> awaitDeliveries(String eventId, int count) {
        await().atMost(WAIT).until(() -> receiver().deliveriesOf(eventId).size() >= count);
        return receiver().deliveriesOf(eventId);
    }

    private static JsonNode eventOf(WebhookReceiver.Delivery delivery) {
        return JsonMapper.shared().readTree(delivery.body());
    }
}
