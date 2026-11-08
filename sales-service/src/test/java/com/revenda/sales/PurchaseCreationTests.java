package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.sales.FakeNeighbors.Call;
import com.revenda.sales.FakeNeighbors.Reply;
import com.revenda.sales.infrastructure.adapters.out.http.NeighborsProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import tools.jackson.databind.JsonNode;

/** UC-08: o pedido de compra, do caminho feliz a cada falha do caminho síncrono. */
@ExtendWith(OutputCaptureExtension.class)
class PurchaseCreationTests extends SalesApiTest {

    private static final Duration LONGER_THAN_THE_READ_TIMEOUT = Duration.ofMillis(1500);

    @Autowired
    private NeighborsProperties neighbors;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClients;

    @Test
    void theCustomerReservesAndGetsThePixCodeInOneCall() {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        NEIGHBORS.vehicleAnswers(vehicle, Reply.price(4_590_000));
        Instant before = clock.instant();

        ResponseEntity<JsonNode> created = customer(subject).post(PURCHASES,
                Map.of("vehicleId", vehicle, "priceCents", 100));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode purchase = created.getBody();
        UUID id = UUID.fromString(purchase.get("id").asString());
        assertThat(created.getHeaders().getLocation()).hasToString("/purchases/" + id);
        assertThat(purchase.get("status").asString()).isEqualTo("AWAITING_PAYMENT");
        assertThat(purchase.get("closedReason").isNull()).isTrue();
        assertThat(purchase.get("vehicleId").asString()).isEqualTo(vehicle.toString());
        assertThat(purchase.get("customerId").asString()).isEqualTo(FakeNeighbors.customerIdOf(subject).toString());
        assertThat(purchase.get("priceCents").asLong()).as("o preço do estoque, não o do corpo (RN-09)")
                .isEqualTo(4_590_000);
        JsonNode payment = purchase.get("payment");
        assertThat(payment.get("status").asString()).isEqualTo("PENDING");
        assertThat(payment.get("code").asString()).isEqualTo(FakeNeighbors.PIX_CODE);
        Instant dueAt = Instant.parse(payment.get("dueAt").asString());
        assertThat(dueAt).isBetween(before.plus(properties.paymentWindow()).minusSeconds(1),
                clock.instant().plus(properties.paymentWindow()));
        assertThat(steps(purchase)).containsExactly("PENDING/NONE/PURCHASE_REQUESTED",
                "RESERVED/NONE/VEHICLE_RESERVED", "AWAITING_PAYMENT/PENDING/CHARGE_ISSUED");
        assertThat(timelineOf(id)).containsExactly("PENDING/NONE/PURCHASE_REQUESTED", "RESERVED/NONE/VEHICLE_RESERVED",
                "AWAITING_PAYMENT/PENDING/CHARGE_ISSUED");
        assertThat(purchaseRow(id)).containsEntry("price_cents", 4_590_000L).containsEntry("customer_subject", subject);
        assertThat(audited("PURCHASE_CREATED", id)).isEqualTo(1);
        assertThat(outboxOf(id)).as("nada a compensar").isEmpty();
    }

    @Test
    void eachNeighborIsCalledWithItsCredentialAndThePayerOnlyGoesToTheGateway(CapturedOutput output) {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        UUID customerId = FakeNeighbors.customerIdOf(subject);

        Call eligibility = single(NEIGHBORS.callsTo("/internal/customers/by-subject/" + subject));
        Call reservation = single(NEIGHBORS.callsTo("/internal/vehicles/").stream()
                .filter(call -> call.body().contains(id.toString())).toList());
        Call payer = single(NEIGHBORS.callsTo("/internal/customers/" + customerId + "/payer").stream()
                .filter(call -> id.toString().equals(call.purchaseId())).toList());
        Call charge = single(NEIGHBORS.callsTo("/charges").stream()
                .filter(call -> call.body().contains(id.toString())).toList());
        for (Call service : List.of(eligibility, reservation, payer)) {
            assertThat(service.authorization()).as("token de serviço, nunca o do cliente")
                    .startsWith("Bearer token-de-servico-");
            assertThat(service.apiKey()).isNull();
        }
        assertThat(charge.authorization()).isNull();
        assertThat(charge.apiKey()).isEqualTo(neighbors.gatewayApiKey());
        assertThat(reservation.body()).isEqualTo("{\"purchaseId\":\"" + id + "\"}");
        assertThat(charge.body()).contains("\"referenceId\":\"" + id + "\"", "\"amountCents\":8990000",
                "\"name\":\"Maria Exemplo da Silva\"", "\"zipCode\":\"01001000\"");
        assertThat(purchaseRow(id).values()).noneMatch(value -> String.valueOf(value).contains("Maria"));
        assertThat(output.getAll()).doesNotContain("Maria Exemplo", "Praça da Sé", "01001000");
    }

    @Test
    void theSameKeyAndBodyGiveTheSamePurchaseWithoutReservingAgain() {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> first = customer(subject).withIdempotencyKey(key)
                .post(PURCHASES, Map.of("vehicleId", vehicle));
        ResponseEntity<JsonNode> again = customer(subject).withIdempotencyKey(key)
                .post(PURCHASES, Map.of("vehicleId", vehicle));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(NEIGHBORS.callsTo("/internal/vehicles/" + vehicle)).hasSize(1);
        assertThat(jdbc.sql("SELECT count(*) FROM purchase WHERE vehicle_id = :vehicle").param("vehicle", vehicle)
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void theSameKeyWithAnotherBodyIsRefusedAndWithoutAKeyNothingStarts() {
        String subject = newCustomer();
        String key = UUID.randomUUID().toString();
        customer(subject).withIdempotencyKey(key).post(PURCHASES, Map.of("vehicleId", UUID.randomUUID()));

        ResponseEntity<JsonNode> reused = customer(subject).withIdempotencyKey(key)
                .post(PURCHASES, Map.of("vehicleId", UUID.randomUUID()));
        UUID vehicle = UUID.randomUUID();
        ResponseEntity<JsonNode> withoutKey = customer(newCustomer()).withoutIdempotencyKey()
                .post(PURCHASES, Map.of("vehicleId", vehicle));

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(reused.getBody().get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(withoutKey.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(withoutKey.getBody().get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(NEIGHBORS.callsTo("/internal/vehicles/" + vehicle)).isEmpty();
    }

    @Test
    void anotherCustomerReservedFirstSoThePurchaseIsRejectedWithNothingToUndo() {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        NEIGHBORS.vehicleAnswers(vehicle, Reply.unavailableVehicle());
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> refused = customer(subject).withIdempotencyKey(key)
                .post(PURCHASES, Map.of("vehicleId", vehicle));
        ResponseEntity<JsonNode> again = customer(subject).withIdempotencyKey(key)
                .post(PURCHASES, Map.of("vehicleId", vehicle));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code").asString()).isEqualTo("VEHICLE_UNAVAILABLE");
        assertThat(refused.getBody().get("message").asString()).contains("reservado por outra pessoa");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asString()).isEqualTo("VEHICLE_UNAVAILABLE");
        UUID id = purchaseOf(vehicle);
        assertThat(purchaseRow(id)).containsEntry("status", "REJECTED")
                .containsEntry("closed_reason", "VEHICLE_UNAVAILABLE").containsEntry("price_cents", null);
        assertThat(timelineOf(id)).containsExactly("PENDING/NONE/PURCHASE_REQUESTED",
                "REJECTED/NONE/VEHICLE_UNAVAILABLE");
        assertThat(outboxOf(id)).isEmpty();
        assertThat(NEIGHBORS.callsTo("/charges")).noneMatch(call -> call.body().contains(id.toString()));
        assertThat(buy(subject, UUID.randomUUID()).getStatusCode()).as("recusada não conta como ativa (RN-10)")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void theStockDownMakesThePurchaseFailAndReleasesTheVehicleJustInCase() {
        assertReservationFailure(Reply.status(500));
    }

    @Test
    void theStockTooSlowToAnswerIsTheSameAsDown() {
        assertReservationFailure(Reply.price(8_990_000).after(LONGER_THAN_THE_READ_TIMEOUT));
    }

    @Test
    void withoutAServiceTokenNothingStarts() {
        String subject = newCustomer();
        authorizedClients.removeAuthorizedClient(neighbors.clientRegistration(), neighbors.clientRegistration());
        NEIGHBORS.tokenAnswers(Reply.status(503));

        ResponseEntity<JsonNode> refused = buy(subject, UUID.randomUUID());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(refused.getBody().get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(purchasesOf(subject)).as("o cadastro não foi consultado: a compra nem começa").isZero();
    }

    @Test
    void theServiceTokenIsAskedOnceForTheWholeServiceNotPerCustomer() {
        authorizedClients.removeAuthorizedClient(neighbors.clientRegistration(), neighbors.clientRegistration());
        int before = NEIGHBORS.callsTo("/token").size();

        awaitingPurchase(newCustomer());
        awaitingPurchase(newCustomer());

        assertThat(NEIGHBORS.callsTo("/token").size() - before).isEqualTo(1);
    }

    @Test
    void theGatewayDownMakesThePurchaseFailReleasingTheVehicleAndCancellingAnyCharge() {
        NEIGHBORS.chargeCreationAnswers(Reply.status(500));
        assertChargeFailure();
    }

    @Test
    void thePayerDataUnavailableIsAChargeNotIssued() {
        NEIGHBORS.payerAnswers(Reply.status(503));
        assertChargeFailure();
    }

    @Test
    void aChargeCreatedWhoseAnswerWasLostIsFoundByThePurchaseAndCancelled() {
        String subject = newCustomer();
        NEIGHBORS.chargeCreatedButAnswersAfter(LONGER_THAN_THE_READ_TIMEOUT);

        ResponseEntity<JsonNode> refused = buy(subject, UUID.randomUUID());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        UUID id = lastPurchaseOf(subject);
        assertThat(purchaseRow(id)).containsEntry("status", "FAILED").containsEntry("closed_reason", "CHARGE_FAILED")
                .containsEntry("charge_id", null);
        NEIGHBORS.reset();
        dispatch();
        assertThat(NEIGHBORS.chargeOf(id).status()).as("achada pela compra e cancelada").isEqualTo("CANCELLED");
        assertThat(outboxRowsOf(id)).allSatisfy(row -> assertThat(row).containsEntry("status", "DONE"));
    }

    @Test
    void withoutAProfileOrUnderageNothingIsReserved() {
        String withoutProfile = newCustomer();
        String underage = newCustomer();
        NEIGHBORS.eligibilityAnswers(withoutProfile, Reply.ineligible("PROFILE_MISSING", null));
        NEIGHBORS.eligibilityAnswers(underage, Reply.ineligible("UNDERAGE", UUID.randomUUID()));
        UUID vehicle = UUID.randomUUID();

        ResponseEntity<JsonNode> noProfile = buy(withoutProfile, vehicle);
        ResponseEntity<JsonNode> tooYoung = buy(underage, vehicle);

        assertThat(noProfile.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(noProfile.getBody().get("code").asString()).isEqualTo("CUSTOMER_PROFILE_REQUIRED");
        assertThat(tooYoung.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(tooYoung.getBody().get("code").asString()).isEqualTo("CUSTOMER_UNDERAGE");
        assertThat(NEIGHBORS.callsTo("/internal/vehicles/" + vehicle)).isEmpty();
        assertThat(purchasesOf(withoutProfile) + purchasesOf(underage)).isZero();
    }

    @Test
    void theRegistryDownMeansThePurchaseNeverStarts() {
        String subject = newCustomer();
        NEIGHBORS.eligibilityAnswers(subject, Reply.status(500));

        ResponseEntity<JsonNode> refused = buy(subject, UUID.randomUUID());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(refused.getBody().get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(purchasesOf(subject)).isZero();
    }

    @Test
    void aCustomerWithAPurchaseInProgressCannotStartAnother() {
        String subject = newCustomer();
        awaitingPurchase(subject);
        UUID second = UUID.randomUUID();

        ResponseEntity<JsonNode> refused = buy(subject, second);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code").asString()).isEqualTo("ACTIVE_PURCHASE_EXISTS");
        assertThat(NEIGHBORS.callsTo("/internal/vehicles/" + second)).isEmpty();
    }

    @RepeatedTest(5)
    void twoPurchasesOfTheSameCustomerAtOnceLeaveOne() throws Exception {
        String subject = newCustomer();

        List<ResponseEntity<JsonNode>> answers = atOnce(
                () -> buy(subject, UUID.randomUUID()),
                () -> buy(subject, UUID.randomUUID()));

        assertThat(answers).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(answers).filteredOn(answer -> answer.getStatusCode() == HttpStatus.CONFLICT)
                .allSatisfy(answer -> assertThat(answer.getBody().get("code").asString())
                        .isEqualTo("ACTIVE_PURCHASE_EXISTS"));
        assertThat(purchasesOf(subject)).isEqualTo(1);
    }

    @Test
    void thePurchaseThatLeftPendingDuringTheReservationGetsTheVehicleReleasedAgain() throws Exception {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        NEIGHBORS.vehicleAnswers(vehicle, Reply.price(8_990_000).after(Duration.ofMillis(300)));

        List<ResponseEntity<JsonNode>> answers = atOnce(() -> buy(subject, vehicle), () -> {
            recoverWhileReserving(vehicle);
            return null;
        });

        assertThat(answers.getFirst().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        UUID id = purchaseOf(vehicle);
        assertThat(purchaseRow(id)).containsEntry("status", "FAILED");
        assertThat(outboxOf(id)).containsExactly("VEHICLE_COMMANDS ReleaseVehicle");
    }

    @Test
    void theBodyIsValidatedBeforeAnything() {
        ResponseEntity<JsonNode> empty = customer(newCustomer()).post(PURCHASES, Map.of());
        ResponseEntity<JsonNode> notAnId = customer(newCustomer()).post(PURCHASES, Map.of("vehicleId", "carro"));

        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody().get("fields").get(0).get("field").asString()).isEqualTo("vehicleId");
        assertThat(empty.getBody().get("fields").get(0).get("code").asString()).isEqualTo("REQUIRED");
        assertThat(notAnId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notAnId.getBody().get("fields").get(0).get("code").asString()).isEqualTo("TYPE_INVALID");
    }

    @Test
    void onlyACustomerBuys() {
        Map<String, UUID> body = Map.of("vehicleId", UUID.randomUUID());

        assertThat(api().asSeller(newCustomer()).post(PURCHASES, body).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().asAdmin(newCustomer()).post(PURCHASES, body).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().asService("customers-service", "sales.read-retention").post(PURCHASES, body)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().anonymous().post(PURCHASES, body).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void assertReservationFailure(Reply stock) {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        NEIGHBORS.vehicleAnswers(vehicle, stock);

        ResponseEntity<JsonNode> refused = buy(subject, vehicle);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(refused.getBody().get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        UUID id = purchaseOf(vehicle);
        assertThat(purchaseRow(id)).containsEntry("status", "FAILED")
                .containsEntry("closed_reason", "RESERVATION_FAILED").containsEntry("price_cents", null);
        assertThat(timelineOf(id)).containsExactly("PENDING/NONE/PURCHASE_REQUESTED",
                "FAILED/NONE/RESERVATION_FAILED");
        assertThat(outboxOf(id)).containsExactly("VEHICLE_COMMANDS ReleaseVehicle");
        assertThat(outboxRowsOf(id).getFirst()).containsEntry("group_key", vehicle.toString());
        assertThat(buy(subject, UUID.randomUUID()).getStatusCode()).as("a compra falhada não prende o cliente")
                .isEqualTo(HttpStatus.CREATED);
    }

    private void assertChargeFailure() {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();

        ResponseEntity<JsonNode> refused = buy(subject, vehicle);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(refused.getBody().get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        UUID id = purchaseOf(vehicle);
        assertThat(purchaseRow(id)).containsEntry("status", "FAILED").containsEntry("closed_reason", "CHARGE_FAILED")
                .containsEntry("price_cents", FakeNeighbors.DEFAULT_PRICE_CENTS);
        assertThat(timelineOf(id)).containsExactly("PENDING/NONE/PURCHASE_REQUESTED", "RESERVED/NONE/VEHICLE_RESERVED",
                "FAILED/NONE/CHARGE_FAILED");
        assertThat(outboxOf(id)).containsExactly("VEHICLE_COMMANDS ReleaseVehicle", "PAYMENT_GATEWAY CancelCharge");
        assertThat(outboxRowsOf(id).get(1)).containsEntry("group_key", id.toString());
    }

    private void recoverWhileReserving(UUID vehicle) {
        Awaitility.await().atMost(PATIENCE).until(() -> jdbc.sql(
                        "SELECT count(*) FROM purchase WHERE vehicle_id = :vehicle AND status = 'PENDING'")
                .param("vehicle", vehicle).query(Integer.class).single() == 1);
        jdbc.sql("""
                        UPDATE purchase SET status = 'FAILED', closed_reason = 'RECOVERY_TIMEOUT', version = version + 1
                         WHERE vehicle_id = :vehicle AND status = 'PENDING'""")
                .param("vehicle", vehicle).update();
    }

    private UUID purchaseOf(UUID vehicle) {
        return jdbc.sql("SELECT id FROM purchase WHERE vehicle_id = :vehicle").param("vehicle", vehicle)
                .query(UUID.class).single();
    }

    private UUID lastPurchaseOf(String subject) {
        return jdbc.sql("SELECT id FROM purchase WHERE customer_subject = :subject ORDER BY created_at DESC LIMIT 1")
                .param("subject", subject).query(UUID.class).single();
    }

    private int purchasesOf(String subject) {
        return jdbc.sql("SELECT count(*) FROM purchase WHERE customer_subject = :subject").param("subject", subject)
                .query(Integer.class).single();
    }

    private static List<String> steps(JsonNode purchase) {
        return purchase.get("timeline").valueStream().map(step -> step.get("status").asString() + "/"
                + step.get("paymentStatus").asString() + "/" + step.get("reason").asString()).toList();
    }

    private static Call single(List<Call> calls) {
        assertThat(calls).hasSize(1);
        return calls.getFirst();
    }
}
