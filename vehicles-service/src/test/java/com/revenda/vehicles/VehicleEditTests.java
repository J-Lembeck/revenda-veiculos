package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class VehicleEditTests extends VehiclesApiTest {

    @Test
    void theSellerEditsTheVehicleAndGetsTheNextVersionWithoutAnIdempotencyKey() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).withoutIdempotencyKey()
                .put(vehicle.path(), vehicle.edit(Map.of("color", "Grafite", "priceCents", 9_490_000L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode edited = response.getBody();
        assertThat(edited.get("id").asString()).isEqualTo(vehicle.id().toString());
        assertThat(edited.get("version").asInt()).isEqualTo(1);
        assertThat(edited.get("color").asString()).isEqualTo("Grafite");
        assertThat(edited.get("priceCents").asLong()).isEqualTo(9_490_000L);
        assertThat(edited.get("status").asString()).isEqualTo("AVAILABLE");
        assertThat(edited.get("plate").asString()).isEqualTo(vehicle.body().get("plate"));
        assertThat(edited.get("updatedAt").asString()).isNotEqualTo(edited.get("createdAt").asString());
        assertThat(row(vehicle.id())).containsEntry("color", "Grafite").containsEntry("price_cents", 9_490_000L)
                .containsEntry("version", 1);
    }

    @Test
    void theEditIsAuditedWithTheVehicleBeforeAndAfter() {
        Registered vehicle = register();

        api().asSeller(SELLER).put(vehicle.path(), vehicle.edit(Map.of("priceCents", 9_490_000L)));

        Map<String, Object> audit = jdbc.sql("""
                        SELECT actor_id, actor_role, summary,
                               data -> 'before' ->> 'priceCents' AS price_before,
                               data -> 'after' ->> 'priceCents' AS price_after,
                               data -> 'before' ->> 'version' AS version_before,
                               data -> 'after' ->> 'version' AS version_after
                          FROM audit_log WHERE action = 'VEHICLE_UPDATED' AND entity_id = :id""")
                .param("id", vehicle.id())
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_id", SELLER).containsEntry("actor_role", "SELLER")
                .containsEntry("summary", "Veículo editado: Chevrolet Onix 2023/2024")
                .containsEntry("price_before", "8990000").containsEntry("price_after", "9490000")
                .containsEntry("version_before", "0").containsEntry("version_after", "1");
    }

    @Test
    void theAdminEditsToo() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).put(vehicle.path(), vehicle.edit(Map.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void editingTwiceNeedsTheVersionReturnedByTheFirstEdit() {
        Registered vehicle = register();
        api().asSeller(SELLER).put(vehicle.path(), vehicle.edit(Map.of("mileageKm", 19_000)));

        ResponseEntity<JsonNode> second = api().asSeller(SELLER)
                .put(vehicle.path(), vehicle.edit(Map.of("mileageKm", 19_500, "version", 1)));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("version").asInt()).isEqualTo(2);
    }

    @Test
    void anOldVersionIsAConflictAndNothingChangesNorIsAudited() {
        Registered vehicle = register();
        api().asSeller(SELLER).put(vehicle.path(), vehicle.edit(Map.of("color", "Grafite")));

        ResponseEntity<JsonNode> stale = api().asSeller(SELLER)
                .put(vehicle.path(), vehicle.edit(Map.of("color", "Azul", "version", 0)));

        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code").asString()).isEqualTo("VERSION_CONFLICT");
        assertThat(row(vehicle.id())).containsEntry("color", "Grafite").containsEntry("version", 1);
        assertThat(updatesAudited(vehicle.id())).isEqualTo(1);
    }

    @RepeatedTest(5)
    void ofTwoSimultaneousEditsWithTheSameVersionExactlyOneWins() throws Exception {
        Registered vehicle = register();
        CyclicBarrier start = new CyclicBarrier(2);
        List<CompletableFuture<ResponseEntity<JsonNode>>> edits = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (String color : List.of("Azul", "Verde")) {
                edits.add(CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return api().asSeller(SELLER).put(vehicle.path(), vehicle.edit(Map.of("color", color)));
                }, pool));
            }
        }

        List<ResponseEntity<JsonNode>> responses = edits.stream().map(CompletableFuture::join).toList();
        assertThat(responses).extracting(response -> response.getStatusCode().value())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(responses).filteredOn(response -> response.getStatusCode().value() == 409)
                .extracting(response -> response.getBody().get("code").asString())
                .containsExactly("VERSION_CONFLICT");
        assertThat(row(vehicle.id())).containsEntry("version", 1);
        assertThat(updatesAudited(vehicle.id())).isEqualTo(1);
    }

    @Test
    void aReservedVehicleIsEditableAndTheNewPriceDoesNotTouchThePurchase() {
        Registered vehicle = register();
        markReserved(vehicle.id());
        Object purchase = row(vehicle.id()).get("purchase_id");

        ResponseEntity<JsonNode> response = api().asSeller(SELLER)
                .put(vehicle.path(), vehicle.edit(Map.of("priceCents", 7_990_000L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asString()).isEqualTo("RESERVED");
        assertThat(row(vehicle.id())).containsEntry("status", "RESERVED").containsEntry("purchase_id", purchase)
                .containsEntry("price_cents", 7_990_000L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLD", "DELIVERED"})
    void aSoldOrDeliveredVehicleIsNotEditable(String status) {
        Registered vehicle = register();
        markSold(vehicle.id(), 8_990_000L, status.equals("DELIVERED"));

        ResponseEntity<JsonNode> response = api().asSeller(SELLER)
                .put(vehicle.path(), vehicle.edit(Map.of("priceCents", 7_990_000L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VEHICLE_NOT_EDITABLE");
        assertThat(row(vehicle.id())).containsEntry("price_cents", 8_990_000L).containsEntry("version", 0);
        assertThat(updatesAudited(vehicle.id())).isZero();
    }

    @Test
    void anUnknownVehicleIsNotFound() {
        ResponseEntity<JsonNode> response = api().asSeller(SELLER)
                .put("/vehicles/" + UUID.randomUUID(), register().edit(Map.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"plate", "vin", "renavam"})
    void anIdentifierOfAnotherVehicleIsRefusedAndNothingChanges(String identifier) {
        Registered other = register();
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().asSeller(SELLER)
                .put(vehicle.path(), vehicle.edit(Map.of(identifier, other.body().get(identifier))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString())
                .isEqualTo(identifier.toUpperCase() + "_ALREADY_REGISTERED");
        assertThat(row(vehicle.id())).containsEntry(identifier, vehicle.body().get(identifier))
                .containsEntry("version", 0);
        assertThat(updatesAudited(vehicle.id())).isZero();
    }

    @ParameterizedTest(name = "{0} = {1} → {2}")
    @MethodSource("com.revenda.vehicles.VehicleRegistrationTests#invalidFields")
    void anInvalidFieldIsRefusedWithItsCodeAndNothingChanges(String field, Object value, String code) {
        Registered vehicle = register();
        Map<String, Object> changes = new HashMap<>();
        changes.put(field, value);

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).put(vehicle.path(), vehicle.edit(changes));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldsOf(response)).contains(field + " " + code);
        assertThat(row(vehicle.id())).containsEntry("version", 0);
    }

    @Test
    void theVersionIsRequired() {
        Registered vehicle = register();
        Map<String, Object> body = vehicle.edit(Map.of());
        body.remove("version");

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).put(vehicle.path(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactly("version REQUIRED");
    }

    @Test
    void aCustomerCannotEditAndNothingChanges() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().asCustomer(CUSTOMER)
                .put(vehicle.path(), vehicle.edit(Map.of("priceCents", 100L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(row(vehicle.id())).containsEntry("version", 0);
    }

    @Test
    void withoutATokenTheAnswerIsUnauthenticated() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().put(vehicle.path(), vehicle.edit(Map.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    private int updatesAudited(UUID id) {
        return jdbc.sql("SELECT count(*) FROM audit_log WHERE action = 'VEHICLE_UPDATED' AND entity_id = :id")
                .param("id", id).query(Integer.class).single();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
