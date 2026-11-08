package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.webhook.WebhookSignature;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchasePaths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** UC-09: o webhook assinado do gateway — assinatura, carimbo, repetição, valor e pagamento atrasado. */
class PaymentWebhookTests extends SalesApiTest {

    private static final long PRICE = FakeNeighbors.DEFAULT_PRICE_CENTS;

    @Test
    void thePaymentWithTheChargedAmountPaysThePurchaseAndSellsTheVehicle() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        String eventId = newEventId();
        NEIGHBORS.markPaid(id, PRICE);

        ResponseEntity<JsonNode> answer = webhook(paymentEvent(eventId, "CHARGE_PAID", chargeIdOf(id), id, PRICE));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purchaseRow(id)).containsEntry("status", "PAID").containsEntry("payment_status", "PAID")
                .containsEntry("paid_amount_cents", PRICE);
        assertThat(((Timestamp) purchaseRow(id).get("paid_at")).toInstant())
                .isEqualTo(Instant.parse("2026-11-06T14:10:00Z"));
        assertThat(timelineOf(id)).last().isEqualTo("PAID/PAID/PAYMENT_CONFIRMED");
        assertThat(transitionActorsOf(id)).last().isEqualTo("SYSTEM");
        assertThat(outboxOf(id)).containsExactly("VEHICLE_COMMANDS MarkVehicleSold");
        JsonNode payload = json.readTree((String) outboxRowsOf(id).getFirst().get("payload"));
        assertThat(payload.get("vehicleId").asString()).isEqualTo(purchaseRow(id).get("vehicle_id").toString());
        assertThat(payload.get("purchaseId").asString()).isEqualTo(id.toString());
        assertThat(payload.get("soldPriceCents").asLong()).isEqualTo(PRICE);
        assertThat(payload.get("soldAt").asString()).isEqualTo("2026-11-06T14:10:00Z");
        assertThat(processed(eventId)).isEqualTo(1);
    }

    @Test
    void theSameEventTwiceIsAppliedOnce() {
        UUID id = awaitingPurchase(newCustomer());
        String body = paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id, PRICE);

        ResponseEntity<JsonNode> first = webhook(body);
        ResponseEntity<JsonNode> again = webhook(body);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timelineOf(id)).filteredOn(step -> step.startsWith("PAID")).hasSize(1);
        assertThat(outboxOf(id)).containsExactly("VEHICLE_COMMANDS MarkVehicleSold");
    }

    @Test
    void anotherEventOfTheSamePaymentChangesNothing() {
        UUID id = awaitingPurchase(newCustomer());
        webhook(paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id, PRICE));

        ResponseEntity<JsonNode> again = webhook(paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id, PRICE));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purchaseRow(id)).containsEntry("version", 3);
        assertThat(outboxOf(id)).containsExactly("VEHICLE_COMMANDS MarkVehicleSold");
    }

    @Test
    void aWrongSignatureIsRefusedAuditedAndChangesNothing() {
        UUID id = awaitingPurchase(newCustomer());
        String body = paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id, PRICE);
        int rejectedBefore = rejections();

        ResponseEntity<JsonNode> forged = webhook(body, new WebhookSignature("outro-segredo-qualquer")
                .sign(bytes(body), clock.instant()));
        ResponseEntity<JsonNode> unsigned = webhook(body, null);
        ResponseEntity<JsonNode> garbage = webhook(body, "t=abc,v1=xyz");
        ResponseEntity<JsonNode> otherBody = webhook(body, sign(paymentEvent(newEventId(), "CHARGE_PAID",
                chargeIdOf(id), id, 1), clock.instant()));

        for (ResponseEntity<JsonNode> refused : List.of(forged, unsigned, garbage, otherBody)) {
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(refused.getBody().get("code").asString()).isEqualTo("INVALID_SIGNATURE");
            assertThat(refused.getBody().get("requestId").asString()).isNotBlank();
        }
        assertThat(purchaseRow(id)).containsEntry("status", "AWAITING_PAYMENT");
        assertThat(rejections() - rejectedBefore).isEqualTo(4);
        assertThat(jdbc.sql("SELECT actor_type FROM audit_log WHERE action = 'PAYMENT_WEBHOOK_REJECTED' "
                + "ORDER BY id DESC LIMIT 1").query(String.class).single()).isEqualTo("SYSTEM");
    }

    @Test
    void aStampOutsideTheWindowIsAReplayEvenWithTheRightSecret() {
        UUID id = awaitingPurchase(newCustomer());
        String body = paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id, PRICE);
        Duration beyond = properties.webhookTolerance().plusSeconds(30);

        ResponseEntity<JsonNode> old = webhook(body, sign(body, clock.instant().minus(beyond)));
        ResponseEntity<JsonNode> future = webhook(body, sign(body, clock.instant().plus(beyond)));
        ResponseEntity<JsonNode> withinWindow = webhook(body, sign(body, clock.instant().minusSeconds(60)));

        assertThat(old.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(future.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(withinWindow.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purchaseRow(id)).containsEntry("status", "PAID");
    }

    @Test
    void aPaymentWithAnotherAmountFailsThePurchaseRefundsAndReleases() {
        UUID id = awaitingPurchase(newCustomer());

        ResponseEntity<JsonNode> answer = webhook(paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id,
                PRICE - 100));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purchaseRow(id)).containsEntry("status", "FAILED").containsEntry("closed_reason", "PAYMENT_MISMATCH")
                .containsEntry("payment_status", "REFUND_REQUESTED").containsEntry("paid_amount_cents", PRICE - 100);
        assertThat(outboxOf(id)).containsExactly("PAYMENT_GATEWAY RefundCharge", "PAYMENT_GATEWAY CancelCharge",
                "VEHICLE_COMMANDS ReleaseVehicle");
    }

    @Test
    void aPaymentAfterTheCancellationIsRefundedAndThePurchaseDoesNotRevive() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        customer(subject).post(PURCHASES + "/" + id + "/cancellation", null);

        ResponseEntity<JsonNode> late = pay(id);

        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purchaseRow(id)).containsEntry("status", "CANCELLED").containsEntry("closed_reason", "CUSTOMER_GAVE_UP")
                .containsEntry("payment_status", "REFUND_REQUESTED");
        assertThat(timelineOf(id)).last().isEqualTo("CANCELLED/REFUND_REQUESTED/LATE_PAYMENT");
        assertThat(outboxOf(id)).containsExactly("PAYMENT_GATEWAY CancelCharge", "VEHICLE_COMMANDS ReleaseVehicle",
                "PAYMENT_GATEWAY RefundCharge");
    }

    @Test
    void aPaymentForAChargeTheSagaNeverLearnedIsRefundedWithTheEventCharge() {
        Purchase failed = persist(PurchasePaths.chargeFailed());
        String charge = "chg_" + UUID.randomUUID().toString().replace("-", "");

        ResponseEntity<JsonNode> late = webhook(paymentEvent(newEventId(), "CHARGE_PAID", charge, failed.id(), PRICE));

        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purchaseRow(failed.id())).containsEntry("status", "FAILED").containsEntry("charge_id", charge)
                .containsEntry("payment_status", "REFUND_REQUESTED");
        JsonNode refund = json.readTree((String) outboxRowsOf(failed.id()).getLast().get("payload"));
        assertThat(refund.get("chargeId").asString()).isEqualTo(charge);
    }

    @Test
    void theConfirmedRefundClosesThePaymentAndARepeatedConfirmationChangesNothing() {
        UUID id = awaitingPurchase(newCustomer());
        webhook(paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id, PRICE - 1));

        ResponseEntity<JsonNode> refunded = webhook(paymentEvent(newEventId(), "CHARGE_REFUNDED", chargeIdOf(id), id,
                PRICE - 1));
        ResponseEntity<JsonNode> again = webhook(paymentEvent(newEventId(), "CHARGE_REFUNDED", chargeIdOf(id), id,
                PRICE - 1));

        assertThat(refunded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purchaseRow(id)).containsEntry("status", "FAILED").containsEntry("payment_status", "REFUNDED");
        assertThat(timelineOf(id)).filteredOn(step -> step.endsWith("REFUND_CONFIRMED")).hasSize(1);
    }

    @Test
    void aPaymentBeforeTheChargeIsIssuedIsNotRecordedSoTheGatewayDeliversAgain() {
        Purchase reserved = persist(PurchasePaths.reserved());
        String eventId = newEventId();

        ResponseEntity<JsonNode> early = webhook(paymentEvent(eventId, "CHARGE_PAID", "chg_cedo", reserved.id(), PRICE));

        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(early.getBody()).isNull();
        assertThat(purchaseRow(reserved.id())).containsEntry("status", "RESERVED").containsEntry("version", 1);
        assertThat(processed(eventId)).as("o mesmo evento entra na reentrega").isZero();
    }

    @Test
    void anEventThatContradictsThePurchaseIsNotApplied() {
        UUID id = awaitingPurchase(newCustomer());
        List<String> contradictions = List.of(
                paymentEvent(newEventId(), "CHARGE_PAID", "chg_de_outra_cobranca", id, PRICE),
                paymentEvent(newEventId(), "CHARGE_REFUNDED", chargeIdOf(id), id, PRICE),
                paymentEvent(newEventId(), "CHARGE_PAID", "chg_qualquer", UUID.randomUUID(), PRICE),
                paymentEvent(newEventId(), "CHARGE_PAID", "chg_qualquer", "nao-e-uma-compra", PRICE));

        for (String event : contradictions) {
            ResponseEntity<JsonNode> refused = webhook(event);
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getBody()).isNull();
        }
        assertThat(purchaseRow(id)).containsEntry("status", "AWAITING_PAYMENT").containsEntry("payment_status", "PENDING");
    }

    @Test
    void aSignedBodyMissingFieldsIsRefusedWithTheFields() {
        String body = "{\"type\":\"CHARGE_SETTLED\",\"amountCents\":0}";

        ResponseEntity<JsonNode> refused = webhook(body);
        ResponseEntity<JsonNode> notJson = webhook("isto não é json");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(refused.getBody().get("fields").valueStream()
                .map(field -> field.get("field").asString() + " " + field.get("code").asString()).toList())
                .containsExactlyInAnyOrder("eventId REQUIRED", "chargeId REQUIRED", "referenceId REQUIRED",
                        "type INVALID", "amountCents OUT_OF_RANGE", "occurredAt REQUIRED");
        assertThat(notJson.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theWebhookNeedsNoTokenButAPersonTokenDoesNotReplaceTheSignature() {
        UUID id = awaitingPurchase(newCustomer());
        String body = paymentEvent(newEventId(), "CHARGE_PAID", chargeIdOf(id), id, PRICE);

        ResponseEntity<JsonNode> withToken = api().asAdmin(newCustomer()).post(WEBHOOK, json.readTree(body));

        assertThat(withToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(withToken.getBody().get("code").asString()).isEqualTo("INVALID_SIGNATURE");
        assertThat(purchaseRow(id)).containsEntry("status", "AWAITING_PAYMENT");
    }

    private String sign(String body, Instant at) {
        return new WebhookSignature(properties.webhookSecret()).sign(bytes(body), at);
    }

    private int processed(String eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_message WHERE message_id = :id AND source = 'payment-webhook'")
                .param("id", eventId).query(Integer.class).single();
    }

    private int rejections() {
        return jdbc.sql("SELECT count(*) FROM audit_log WHERE action = 'PAYMENT_WEBHOOK_REJECTED'")
                .query(Integer.class).single();
    }

    private List<String> transitionActorsOf(UUID id) {
        return jdbc.sql("SELECT actor_type FROM purchase_transition WHERE purchase_id = :id ORDER BY id")
                .param("id", id).query(String.class).list();
    }
}
