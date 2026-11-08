package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.SyntheticData;
import com.revenda.sales.FakeNeighbors.Call;
import com.revenda.sales.FakeNeighbors.Reply;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchasePaths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** UC-12 (RN-14): a retirada confere o CPF apresentado e é o pivô da saga. */
class PurchasePickupTests extends SalesApiTest {

    @Test
    void theSellerConfirmsThePickupWithTheBuyersCpfAndThePurchaseIsCompleted() {
        UUID id = paidPurchase(newCustomer());

        ResponseEntity<JsonNode> response = pickup(id, FakeNeighbors.BUYER_CPF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asString()).isEqualTo("COMPLETED");
        assertThat(response.getBody().at("/payment/status").asString()).isEqualTo("PAID");
        assertThat(response.getBody().get("timeline").get(4).get("reason").asString()).isEqualTo("PICKUP_CONFIRMED");
        assertThat(purchaseRow(id).get("completed_at")).isNotNull();
        assertThat(timelineOf(id)).last().isEqualTo("COMPLETED/PAID/PICKUP_CONFIRMED");
    }

    @Test
    void thePickupAsksForTheDeliveryAndTheDocumentInTheSameTransaction() {
        UUID id = paidPurchase(newCustomer());
        UUID vehicleId = (UUID) purchaseRow(id).get("vehicle_id");

        pickup(id, FakeNeighbors.BUYER_CPF);

        assertThat(outboxOf(id)).endsWith("VEHICLE_COMMANDS DeliverVehicle", "DOCUMENTS GenerateTransferDocument");
        Map<String, Object> delivery = outboxRowsOf(id).stream()
                .filter(row -> "DeliverVehicle".equals(row.get("type"))).findFirst().orElseThrow();
        assertThat(delivery).containsEntry("group_key", vehicleId.toString());
        JsonNode payload = json.readTree((String) delivery.get("payload"));
        assertThat(payload.get("purchaseId").asString()).isEqualTo(id.toString());
        assertThat(payload.get("deliveredAt").asString()).isNotBlank();
        Map<String, Object> document = outboxRowsOf(id).getLast();
        assertThat(document).containsEntry("group_key", id.toString());
        assertThat((String) document.get("payload")).isEqualTo("{\"purchaseId\": \"" + id + "\"}");
    }

    @Test
    void theConfirmationIsAuditedWithTheSellerAndNoPersonalData() {
        UUID id = paidPurchase(newCustomer());

        pickup(id, FakeNeighbors.BUYER_CPF);

        Map<String, Object> audit = auditRow("PURCHASE_PICKUP_CONFIRMED", id);
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_id", SELLER)
                .containsEntry("actor_role", "SELLER");
        assertThat((String) audit.get("data")).contains("\"COMPLETED\"").doesNotContain(FakeNeighbors.BUYER_CPF);
    }

    @Test
    void theCpfGoesToTheCadastreInTheBodyWithThePurchaseAndAServiceToken() {
        UUID id = paidPurchase(newCustomer());

        pickup(id, SyntheticData.formatCpf(FakeNeighbors.BUYER_CPF));

        Call check = NEIGHBORS.callsTo("/internal/customers/").stream()
                .filter(call -> call.path().endsWith("/cpf-check") && id.toString().equals(call.purchaseId()))
                .findFirst().orElseThrow();
        assertThat(check.path()).isEqualTo("/internal/customers/" + purchaseRow(id).get("customer_id")
                + "/cpf-check").doesNotContain(FakeNeighbors.BUYER_CPF);
        assertThat(check.body()).contains(SyntheticData.formatCpf(FakeNeighbors.BUYER_CPF));
        assertThat(check.authorization()).startsWith("Bearer token-de-servico-");
        assertThat(statusOf(id)).isEqualTo("COMPLETED");
    }

    @Test
    void anotherPersonsCpfIsRefusedAndThePurchaseStaysPaid() {
        UUID id = paidPurchase(newCustomer());
        String other = otherCpf();

        ResponseEntity<JsonNode> response = pickup(id, other);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("IDENTITY_MISMATCH");
        assertThat(response.getBody().toString()).doesNotContain(other).doesNotContain(FakeNeighbors.BUYER_CPF);
        assertThat(statusOf(id)).isEqualTo("PAID");
        assertThat(outboxOf(id)).doesNotContain("VEHICLE_COMMANDS DeliverVehicle");
        assertThat(audited("PURCHASE_PICKUP_CONFIRMED", id)).isZero();
    }

    @Test
    void theRefusedPickupIsAuditedWithWhoTried() {
        UUID id = paidPurchase(newCustomer());

        pickup(id, otherCpf());

        Map<String, Object> audit = auditRow("PURCHASE_PICKUP_REJECTED", id);
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_id", SELLER);
        assertThat((String) audit.get("summary")).contains("não é o do comprador");
    }

    @Test
    void aCpfWithWrongDigitsIsAFieldErrorAndNothingIsAudited() {
        UUID id = paidPurchase(newCustomer());

        ResponseEntity<JsonNode> response = pickup(id, "123.456.789-00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().at("/fields/0/field").asString()).isEqualTo("cpf");
        assertThat(response.getBody().at("/fields/0/code").asString()).isEqualTo("CPF_INVALID");
        assertThat(statusOf(id)).isEqualTo("PAID");
        assertThat(audited("PURCHASE_PICKUP_REJECTED", id)).isZero();
    }

    @Test
    void withoutACpfNothingIsChecked() {
        UUID id = paidPurchase(newCustomer());
        int checksBefore = cpfChecks(id);

        ResponseEntity<JsonNode> blank = pickup(id, " ");
        ResponseEntity<JsonNode> missing = seller().post(PURCHASES + "/" + id + "/pickup", Map.of());

        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody().at("/fields/0/code").asString()).isEqualTo("REQUIRED");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cpfChecks(id)).isEqualTo(checksBefore);
    }

    @Test
    void aPurchaseNotPaidIsRefusedBeforeAnyCpfCheck() {
        UUID awaiting = awaitingPurchase(newCustomer());

        ResponseEntity<JsonNode> response = pickup(awaiting, FakeNeighbors.BUYER_CPF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("PURCHASE_NOT_PAID");
        assertThat(cpfChecks(awaiting)).isZero();
        assertThat(statusOf(awaiting)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void closedOrAlreadyPickedUpPurchasesAreNotPickedUp() {
        for (PurchasePaths.Path path : List.of(PurchasePaths.completed(), PurchasePaths.cancelledAfterPaying(),
                PurchasePaths.expired(), PurchasePaths.paidWrongAmount())) {
            Purchase purchase = persist(path);

            ResponseEntity<JsonNode> response = pickup(purchase.id(), FakeNeighbors.BUYER_CPF);

            assertThat(response.getStatusCode()).as(path.name()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code").asString()).as(path.name()).isEqualTo("PURCHASE_NOT_PAID");
        }
    }

    @Test
    void theSecondPickupOfTheSamePurchaseIsRefused() {
        UUID id = paidPurchase(newCustomer());
        pickup(id, FakeNeighbors.BUYER_CPF);

        ResponseEntity<JsonNode> again = pickup(id, FakeNeighbors.BUYER_CPF);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asString()).isEqualTo("PURCHASE_NOT_PAID");
        assertThat(audited("PURCHASE_PICKUP_CONFIRMED", id)).isEqualTo(1);
    }

    @RepeatedTest(5)
    void ofTwoSimultaneousPickupsExactlyOneConfirms() throws Exception {
        UUID id = paidPurchase(newCustomer());

        List<ResponseEntity<JsonNode>> answers = atOnce(() -> pickup(id, FakeNeighbors.BUYER_CPF),
                () -> pickup(id, FakeNeighbors.BUYER_CPF));

        assertThat(answers).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(timelineOf(id)).filteredOn(step -> step.endsWith("PICKUP_CONFIRMED")).hasSize(1);
        assertThat(outboxOf(id)).filteredOn("VEHICLE_COMMANDS DeliverVehicle"::equals).hasSize(1);
    }

    @RepeatedTest(5)
    void pickupAndCancellationAtOnceLeaveOneOfThem() throws Exception {
        String subject = newCustomer();
        UUID id = paidPurchase(subject);

        List<ResponseEntity<JsonNode>> answers = atOnce(() -> pickup(id, FakeNeighbors.BUYER_CPF),
                () -> customer(subject).post(PURCHASES + "/" + id + "/cancellation", null));

        assertThat(answers).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        String status = statusOf(id);
        assertThat(status).isIn("COMPLETED", "CANCELLED");
        if (status.equals("COMPLETED")) {
            assertThat(outboxOf(id)).doesNotContain("PAYMENT_GATEWAY RefundCharge");
        } else {
            assertThat(outboxOf(id)).doesNotContain("VEHICLE_COMMANDS DeliverVehicle");
        }
    }

    @Test
    void withTheCadastreDownNothingChanges() {
        UUID id = paidPurchase(newCustomer());
        NEIGHBORS.cpfCheckAnswers(Reply.status(500));

        ResponseEntity<JsonNode> failed = pickup(id, FakeNeighbors.BUYER_CPF);
        NEIGHBORS.cpfCheckAnswers(Reply.json(200, "{\"matches\":true}").after(Duration.ofSeconds(1)));
        ResponseEntity<JsonNode> slow = pickup(id, FakeNeighbors.BUYER_CPF);

        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(failed.getBody().get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(slow.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(statusOf(id)).isEqualTo("PAID");
        assertThat(audited("PURCHASE_PICKUP_REJECTED", id)).isZero();
    }

    @Test
    void anUnknownPurchaseIsNotFound() {
        ResponseEntity<JsonNode> response = pickup(UUID.randomUUID(), FakeNeighbors.BUYER_CPF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void onlyTheStaffConfirmsThePickup() {
        String subject = newCustomer();
        UUID id = paidPurchase(subject);
        Map<String, String> body = Map.of("cpf", FakeNeighbors.BUYER_CPF);
        String path = PURCHASES + "/" + id + "/pickup";

        assertThat(customer(subject).post(path, body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().asService("customers-service", "sales.read-retention").post(path, body).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().post(path, body).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf(id)).isEqualTo("PAID");
        assertThat(api().asAdmin("9a3c2f81-administrador").post(path, body).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anIdThatIsNotAUuidIsRefused() {
        ResponseEntity<JsonNode> response = seller().post(PURCHASES + "/compra/pickup",
                Map.of("cpf", FakeNeighbors.BUYER_CPF));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().at("/fields/0/field").asString()).isEqualTo("id");
    }

    private Map<String, Object> auditRow(String action, UUID id) {
        return jdbc.sql("""
                        SELECT actor_type, actor_id, actor_role, summary, data::text AS data
                          FROM audit_log WHERE action = :action AND entity_id = :id""")
                .param("action", action).param("id", id).query().singleRow();
    }

    private static int cpfChecks(UUID purchaseId) {
        return (int) NEIGHBORS.calls().stream()
                .filter(call -> call.path().endsWith("/cpf-check") && purchaseId.toString().equals(call.purchaseId()))
                .count();
    }

    private static String otherCpf() {
        String cpf;
        do {
            cpf = SyntheticData.cpf();
        } while (cpf.equals(FakeNeighbors.BUYER_CPF));
        return cpf;
    }
}
