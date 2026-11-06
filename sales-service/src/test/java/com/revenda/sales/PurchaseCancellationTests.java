package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchasePaths;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** UC-11 (RN-13): a desistência antes e depois de pagar, fora de hora, de outro cliente e junto do pagamento. */
class PurchaseCancellationTests extends SalesApiTest {

    @Test
    void beforePayingTheChargeIsCancelledAndTheVehicleReleased() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);

        ResponseEntity<JsonNode> cancelled = cancel(subject, id);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode purchase = cancelled.getBody();
        assertThat(purchase.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(purchase.get("closedReason").asString()).isEqualTo("CUSTOMER_GAVE_UP");
        assertThat(purchase.get("payment").get("status").asString()).isEqualTo("CANCELLED");
        assertThat(purchase.get("timeline").valueStream().map(step -> step.get("reason").asString()).toList())
                .last().isEqualTo("CUSTOMER_GAVE_UP");
        assertThat(outboxOf(id)).containsExactly("PAYMENT_GATEWAY CancelCharge", "VEHICLE_COMMANDS ReleaseVehicle");
        assertThat(audited("PURCHASE_CANCELLED", id)).isEqualTo(1);
        assertThat(lastActorOf(id)).isEqualTo("USER " + subject);

        dispatch();

        assertThat(NEIGHBORS.chargeOf(id).status()).isEqualTo("CANCELLED");
        UUID vehicle = (UUID) purchaseRow(id).get("vehicle_id");
        assertThat(queuedFor(vehicle, 1)).extracting(QueuedCommand::type).containsExactly("ReleaseVehicle");
        assertThat(outboxRowsOf(id)).allSatisfy(row -> assertThat(row).containsEntry("status", "DONE"));
    }

    @Test
    void afterPayingTheMoneyIsRefundedAndTheSoldVehicleGoesBackOnSale() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        pay(id);

        ResponseEntity<JsonNode> cancelled = cancel(subject, id);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.getBody().get("payment").get("status").asString()).isEqualTo("REFUND_REQUESTED");
        assertThat(outboxOf(id)).containsExactly("VEHICLE_COMMANDS MarkVehicleSold", "PAYMENT_GATEWAY RefundCharge",
                "VEHICLE_COMMANDS ReleaseVehicle");

        dispatch();
        UUID vehicle = (UUID) purchaseRow(id).get("vehicle_id");
        assertThat(queuedFor(vehicle, 2)).extracting(QueuedCommand::type)
                .containsExactly("MarkVehicleSold", "ReleaseVehicle");
        assertThat(NEIGHBORS.chargeOf(id).status()).isEqualTo("REFUNDED");

        webhook(paymentEvent(newEventId(), "CHARGE_REFUNDED", chargeIdOf(id), id, FakeNeighbors.DEFAULT_PRICE_CENTS));
        assertThat(purchaseRow(id)).containsEntry("status", "CANCELLED").containsEntry("payment_status", "REFUNDED");
    }

    @Test
    void whileTheVehicleIsBeingReservedTheCustomerTriesAgainInAMoment() {
        Purchase pending = persist(PurchasePaths.pending());
        Purchase reserved = persist(PurchasePaths.reserved());

        for (Purchase inProgress : List.of(pending, reserved)) {
            ResponseEntity<JsonNode> refused = ownerOf(inProgress)
                    .post(PURCHASES + "/" + inProgress.id() + "/cancellation", null);
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getBody().get("code").asString()).isEqualTo("PURCHASE_IN_PROGRESS");
            assertThat(statusOf(inProgress.id())).isEqualTo(inProgress.status().name());
        }
    }

    @Test
    void afterThePickupOrWhenAlreadyClosedThereIsNothingToGiveUp() {
        Purchase completed = persist(PurchasePaths.completed());
        Purchase expired = persist(PurchasePaths.expired());
        String subject = newCustomer();
        UUID cancelledTwice = awaitingPurchase(subject);
        cancel(subject, cancelledTwice);

        for (ResponseEntity<JsonNode> refused : List.of(
                ownerOf(completed).post(PURCHASES + "/" + completed.id() + "/cancellation", null),
                ownerOf(expired).post(PURCHASES + "/" + expired.id() + "/cancellation", null),
                cancel(subject, cancelledTwice))) {
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getBody().get("code").asString()).isEqualTo("PURCHASE_NOT_CANCELLABLE");
        }
        assertThat(audited("PURCHASE_CANCELLED", cancelledTwice)).isEqualTo(1);
    }

    @Test
    void onlyTheOwnerGivesUpAndAnotherCustomerSeesNothing() {
        String owner = newCustomer();
        UUID id = awaitingPurchase(owner);
        String path = PURCHASES + "/" + id + "/cancellation";

        assertThat(cancel(newCustomer(), id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(cancel(owner, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api().asSeller(newCustomer()).post(path, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().anonymous().post(path, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(customer(owner).post(PURCHASES + "/nao-e-id/cancellation", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(id)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void whenTheCancellationWinsThePaymentThatArrivesBecomesARefund() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);

        cancel(subject, id);
        pay(id);

        assertThat(purchaseRow(id)).containsEntry("status", "CANCELLED").containsEntry("payment_status", "REFUND_REQUESTED");
        assertThat(outboxOf(id)).containsExactly("PAYMENT_GATEWAY CancelCharge", "VEHICLE_COMMANDS ReleaseVehicle",
                "PAYMENT_GATEWAY RefundCharge");
    }

    @RepeatedTest(5)
    void theCancellationAndThePaymentAtOnceEndCancelledWithTheMoneyRefunded() throws Exception {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);

        List<ResponseEntity<JsonNode>> answers = atOnce(() -> cancel(subject, id), () -> pay(id));

        assertThat(answers).extracting(ResponseEntity::getStatusCode).containsOnly(HttpStatus.OK);
        assertThat(purchaseRow(id)).containsEntry("status", "CANCELLED").containsEntry("closed_reason", "CUSTOMER_GAVE_UP")
                .containsEntry("payment_status", "REFUND_REQUESTED");
        assertThat(outboxOf(id)).containsOnlyOnce("PAYMENT_GATEWAY RefundCharge").last()
                .isIn("PAYMENT_GATEWAY RefundCharge", "VEHICLE_COMMANDS ReleaseVehicle");
        assertThat(outboxOf(id)).contains("VEHICLE_COMMANDS ReleaseVehicle");
    }

    private ResponseEntity<JsonNode> cancel(String subject, UUID id) {
        return customer(subject).post(PURCHASES + "/" + id + "/cancellation", null);
    }

    private String lastActorOf(UUID id) {
        return jdbc.sql("SELECT actor_type || ' ' || actor_id FROM purchase_transition WHERE purchase_id = :id "
                + "ORDER BY id DESC LIMIT 1").param("id", id).query(String.class).single();
    }
}
