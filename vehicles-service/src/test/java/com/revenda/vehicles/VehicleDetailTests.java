package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class VehicleDetailTests extends VehiclesApiTest {

    @Test
    void theVisitorSeesAVehicleForSaleWithoutItsIdentifiers() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().get(vehicle.path());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("id").asString()).isEqualTo(vehicle.id().toString());
        assertThat(body.get("priceCents").asLong()).isEqualTo(8_990_000L);
        assertThat(body.has("plate")).isFalse();
        assertThat(body.has("vin")).isFalse();
        assertThat(body.has("renavam")).isFalse();
        assertThat(body.has("version")).isFalse();
    }

    @Test
    void aCustomerSeesThePublicDetailToo() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().asCustomer(CUSTOMER).get(vehicle.path());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().has("plate")).isFalse();
    }

    @Test
    void aReservedVehicleIsNotFoundForTheVisitorNorTheCustomer() {
        Registered vehicle = register();
        markReserved(vehicle.id());

        ResponseEntity<JsonNode> visitor = api().get(vehicle.path());
        ResponseEntity<JsonNode> customer = api().asCustomer(CUSTOMER).get(vehicle.path());

        assertThat(visitor.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(visitor.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(customer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aDeliveredVehicleIsNotFoundForTheVisitor() {
        Registered vehicle = register();
        markSold(vehicle.id(), 8_990_000L, true);

        ResponseEntity<JsonNode> response = api().get(vehicle.path());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theSellerSeesAnyVehicleWithItsIdentifiersStatusAndVersion() {
        Registered vehicle = register();
        markSold(vehicle.id(), 8_500_000L, false);

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).get(vehicle.path());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("status").asString()).isEqualTo("SOLD");
        assertThat(body.get("soldPriceCents").asLong()).isEqualTo(8_500_000L);
        assertThat(body.get("plate").asString()).isEqualTo(vehicle.body().get("plate"));
        assertThat(body.get("vin").asString()).isEqualTo(vehicle.body().get("vin"));
        assertThat(body.get("renavam").asString()).isEqualTo(vehicle.body().get("renavam"));
        assertThat(body.get("version").asInt()).isZero();
    }

    @Test
    void theAdminSeesAReservedVehicle() {
        Registered vehicle = register();
        markReserved(vehicle.id());

        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).get(vehicle.path());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asString()).isEqualTo("RESERVED");
    }

    @Test
    void anUnknownVehicleIsNotFoundForEveryone() {
        String path = "/vehicles/" + UUID.randomUUID();

        assertThat(api().get(path).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api().asSeller(SELLER).get(path).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aBadTokenOnTheOpenDetailIsRefusedInsteadOfBecomingAVisitor() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().as("nao-e-um-jwt").get(vehicle.path());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void anIdThatIsNotAUuidIsAValidationError() {
        ResponseEntity<JsonNode> response = api().get("/vehicles/nao-e-um-id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactly("id TYPE_INVALID");
    }
}
