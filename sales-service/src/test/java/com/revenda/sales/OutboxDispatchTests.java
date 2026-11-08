package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.sales.FakeNeighbors.Reply;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * O despachante do outbox: os comandos do estoque na fila FIFO, no envelope que o consumidor lê, e os do gateway pela
 * API dele; o que falha volta para a fila com espera e não segura os outros grupos.
 */
class OutboxDispatchTests extends SalesApiTest {

    @Test
    void theStockCommandGoesToTheFifoQueueInTheEnvelopeTheStockReads() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        pay(id);
        UUID vehicle = (UUID) purchaseRow(id).get("vehicle_id");
        Map<String, Object> message = outboxRowsOf(id).getFirst();

        dispatch();

        QueuedCommand queued = queuedFor(vehicle, 1).getFirst();
        assertThat(queued.groupId()).isEqualTo(vehicle.toString());
        assertThat(queued.deduplicationId()).isEqualTo(message.get("id").toString());
        JsonNode envelope = queued.envelope();
        assertThat(envelope.propertyNames()).containsExactlyInAnyOrder("id", "type", "payload");
        assertThat(envelope.get("id").asString()).isEqualTo(message.get("id").toString());
        assertThat(envelope.get("type").asString()).isEqualTo("MarkVehicleSold");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("vehicleId", "purchaseId", "soldPriceCents",
                "soldAt");
        assertThat(payload.get("purchaseId").asString()).isEqualTo(id.toString());
        assertThat(payload.get("soldPriceCents").asLong()).isEqualTo(FakeNeighbors.DEFAULT_PRICE_CENTS);
        assertThat(outboxRowsOf(id).getFirst()).containsEntry("status", "DONE");
    }

    @Test
    void theHappyPathEndsPaidWithTheSaleOnTheWayToTheStock() {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        UUID id = UUID.fromString(buy(subject, vehicle).getBody().get("id").asString());

        pay(id);
        dispatch();

        assertThat(purchaseRow(id)).containsEntry("status", "PAID").containsEntry("payment_status", "PAID");
        assertThat(queuedFor(vehicle, 1)).extracting(QueuedCommand::type).containsExactly("MarkVehicleSold");
    }

    @Test
    void aGatewayCommandThatFailsGoesBackToTheQueueAndIsSentWhenTheGatewayReturns() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        customer(subject).post(PURCHASES + "/" + id + "/cancellation", null);
        NEIGHBORS.chargeCommandsAnswer(Reply.status(503));

        dispatch();

        Map<String, Object> cancel = gatewayCommandOf(id);
        assertThat(cancel).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        assertThat(NEIGHBORS.chargeOf(id).status()).isEqualTo("PENDING");
        assertThat(outboxRowsOf(id)).filteredOn(row -> "VEHICLE_COMMANDS".equals(row.get("destination")))
                .allSatisfy(row -> assertThat(row).containsEntry("status", "DONE"));

        NEIGHBORS.reset();
        jdbc.sql("UPDATE outbox_message SET available_at = now() WHERE id = :id").param("id", cancel.get("id"))
                .update();
        dispatch();

        assertThat(gatewayCommandOf(id)).containsEntry("status", "DONE");
        assertThat(NEIGHBORS.chargeOf(id).status()).isEqualTo("CANCELLED");
    }

    @Test
    void aRefundOfAChargeTheGatewayDoesNotKnowIsAFailureNotSilence() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        String unknownCharge = "chg_" + UUID.randomUUID().toString().replace("-", "");
        transaction.executeWithoutResult(status -> jdbc.sql("""
                        UPDATE purchase SET charge_id = :charge WHERE id = :id""")
                .param("charge", unknownCharge).param("id", id).update());
        webhook(paymentEvent(newEventId(), "CHARGE_PAID", unknownCharge, id, FakeNeighbors.DEFAULT_PRICE_CENTS - 1));

        dispatch();

        List<Map<String, Object>> refunds = outboxRowsOf(id).stream()
                .filter(row -> "RefundCharge".equals(row.get("type"))).toList();
        assertThat(refunds).singleElement().satisfies(refund -> assertThat(refund)
                .containsEntry("status", "PENDING").containsEntry("attempts", 1));
    }

    @Test
    void cancellingWithoutTheChargeIdFindsItByThePurchaseOrHasNothingToDo() {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        NEIGHBORS.vehicleAnswers(vehicle, Reply.status(500));
        buy(subject, vehicle);
        UUID failed = jdbc.sql("SELECT id FROM purchase WHERE vehicle_id = :vehicle").param("vehicle", vehicle)
                .query(UUID.class).single();
        transaction.executeWithoutResult(status -> jdbc.sql("""
                        INSERT INTO outbox_message (id, destination, type, aggregate_id, group_key, payload,
                                                    available_at, created_at)
                        VALUES (:id, 'PAYMENT_GATEWAY', 'CancelCharge', :purchase, :group,
                                CAST(:payload AS jsonb), now(), now())""")
                .param("id", UUID.randomUUID()).param("purchase", failed).param("group", failed.toString())
                .param("payload", "{\"purchaseId\":\"" + failed + "\",\"chargeId\":null}").update());

        dispatch();

        assertThat(gatewayCommandOf(failed)).containsEntry("status", "DONE");
        assertThat(NEIGHBORS.callsTo("/charges").stream().filter(call -> call.query() != null
                && call.query().contains(failed.toString()))).hasSize(1);
    }

    private Map<String, Object> gatewayCommandOf(UUID purchaseId) {
        return outboxRowsOf(purchaseId).stream().filter(row -> "PAYMENT_GATEWAY".equals(row.get("destination")))
                .findFirst().orElseThrow();
    }
}
