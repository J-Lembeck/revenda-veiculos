package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class VehicleReservationTests extends VehiclesApiTest {

    private static final int BUYERS = 10;

    @Test
    void theSalesServiceReservesAVehicleForSaleAndGetsThePriceOfTheMoment() {
        Registered vehicle = register(Map.of("priceCents", 7_250_000L));
        UUID purchase = UUID.randomUUID();

        ResponseEntity<JsonNode> response = sales().post(reservations(vehicle), Map.of("purchaseId", purchase));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("priceCents").asLong()).isEqualTo(7_250_000L);
        assertThat(row(vehicle.id())).containsEntry("status", "RESERVED").containsEntry("purchase_id", purchase)
                .containsEntry("version", 1);
    }

    @Test
    void theReservationIsAuditedWithTheServiceAndThePurchase() {
        Registered vehicle = register();
        UUID purchase = UUID.randomUUID();

        sales().post(reservations(vehicle), Map.of("purchaseId", purchase));

        Map<String, Object> audit = jdbc.sql("""
                        SELECT actor_type, actor_id, summary, data -> 'extra' ->> 'purchaseId' AS purchase,
                               data -> 'extra' ->> 'priceCents' AS price
                          FROM audit_log WHERE action = 'VEHICLE_RESERVED' AND entity_id = :id""")
                .param("id", vehicle.id())
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "SERVICE").containsEntry("actor_id", TestTokens.SALES_CLIENT)
                .containsEntry("summary", "Veículo reservado para a compra " + purchase)
                .containsEntry("purchase", purchase.toString()).containsEntry("price", "8990000");
    }

    @Test
    void repeatingForTheSamePurchaseSucceedsWithoutAnotherEffect() {
        Registered vehicle = register();
        UUID purchase = UUID.randomUUID();
        sales().post(reservations(vehicle), Map.of("purchaseId", purchase));

        ResponseEntity<JsonNode> again = sales().post(reservations(vehicle), Map.of("purchaseId", purchase));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().get("priceCents").asLong()).isEqualTo(8_990_000L);
        assertThat(row(vehicle.id())).containsEntry("version", 1);
        assertThat(reservationsAudited(vehicle.id())).isEqualTo(1);
    }

    @Test
    void anotherPurchaseFindsTheVehicleUnavailableAndChangesNothing() {
        Registered vehicle = register();
        UUID first = UUID.randomUUID();
        sales().post(reservations(vehicle), Map.of("purchaseId", first));

        ResponseEntity<JsonNode> second = sales().post(reservations(vehicle), Map.of("purchaseId", UUID.randomUUID()));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code").asString()).isEqualTo("VEHICLE_UNAVAILABLE");
        assertThat(row(vehicle.id())).containsEntry("purchase_id", first).containsEntry("version", 1);
    }

    @Test
    void aSoldVehicleIsUnavailableEvenForThePurchaseThatBoughtIt() {
        Registered vehicle = register();
        markSold(vehicle.id(), 8_990_000L, false);
        Object purchase = row(vehicle.id()).get("purchase_id");

        ResponseEntity<JsonNode> response = sales().post(reservations(vehicle), Map.of("purchaseId", purchase));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VEHICLE_UNAVAILABLE");
        assertThat(row(vehicle.id())).containsEntry("status", "SOLD");
    }

    @Test
    void anUnknownVehicleIsUnavailable() {
        ResponseEntity<JsonNode> response = sales()
                .post("/internal/vehicles/" + UUID.randomUUID() + "/reservations", Map.of("purchaseId", UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VEHICLE_UNAVAILABLE");
    }

    @Test
    void aReservedVehicleLeavesTheShowcase() {
        Registered vehicle = register();

        sales().post(reservations(vehicle), Map.of("purchaseId", UUID.randomUUID()));

        assertThat(api().get(vehicle.path()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @RepeatedTest(20)
    void ofManySimultaneousReservationsExactlyOneWins() throws Exception {
        Registered vehicle = register();
        CyclicBarrier start = new CyclicBarrier(BUYERS);
        List<UUID> purchases = new ArrayList<>();
        List<CompletableFuture<ResponseEntity<JsonNode>>> attempts = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(BUYERS)) {
            for (int i = 0; i < BUYERS; i++) {
                UUID purchase = UUID.randomUUID();
                purchases.add(purchase);
                attempts.add(CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return sales().post(reservations(vehicle), Map.of("purchaseId", purchase));
                }, pool));
            }
        }

        List<Integer> statuses = attempts.stream().map(CompletableFuture::join)
                .map(response -> response.getStatusCode().value()).toList();
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(BUYERS - 1);
        UUID winner = purchases.get(statuses.indexOf(200));
        assertThat(row(vehicle.id())).containsEntry("status", "RESERVED").containsEntry("purchase_id", winner)
                .containsEntry("version", 1);
        assertThat(reservationsAudited(vehicle.id())).isEqualTo(1);
    }

    @Test
    void thePurchaseIsRequired() {
        ResponseEntity<JsonNode> response = sales().post(reservations(register()), Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactly("purchaseId REQUIRED");
    }

    @Test
    void aPurchaseThatIsNotAUuidIsRefused() {
        ResponseEntity<JsonNode> response = sales().post(reservations(register()), Map.of("purchaseId", "compra-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactly("purchaseId TYPE_INVALID");
    }

    @Test
    void peopleCannotReserveEvenTheStaff() {
        Registered vehicle = register();
        Map<String, Object> body = Map.of("purchaseId", UUID.randomUUID());

        ResponseEntity<JsonNode> seller = api().asSeller(SELLER).post(reservations(vehicle), body);
        ResponseEntity<JsonNode> customer = api().asCustomer(CUSTOMER).post(reservations(vehicle), body);

        assertThat(seller.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(customer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(row(vehicle.id())).containsEntry("status", "AVAILABLE");
    }

    @Test
    void aServiceWithoutTheScopeCannotReserve() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().asService(TestTokens.SALES_CLIENT, "customers.read-payer")
                .post(reservations(vehicle), Map.of("purchaseId", UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(row(vehicle.id())).containsEntry("status", "AVAILABLE");
    }

    @Test
    void withoutATokenTheAnswerIsUnauthenticated() {
        ResponseEntity<JsonNode> response = api().post(reservations(register()), Map.of("purchaseId", UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ApiDriver sales() {
        return api().asService(TestTokens.SALES_CLIENT, "vehicles.reserve");
    }

    private static String reservations(Registered vehicle) {
        return "/internal/vehicles/" + vehicle.id() + "/reservations";
    }

    private int reservationsAudited(UUID id) {
        return jdbc.sql("SELECT count(*) FROM audit_log WHERE action = 'VEHICLE_RESERVED' AND entity_id = :id")
                .param("id", id).query(Integer.class).single();
    }
}
