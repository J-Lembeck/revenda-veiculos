package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class VehicleInternalReadTests extends VehiclesApiTest {

    @Test
    void theSalesServiceReadsADeliveredVehicleWithItsIdentification() {
        Registered vehicle = register();
        markSold(vehicle.id(), 8_990_000L, true);

        ResponseEntity<JsonNode> response = sales().get(path(vehicle.id()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("status").asString()).isEqualTo("DELIVERED");
        assertThat(body.get("plate").asString()).isEqualTo(vehicle.body().get("plate"));
        assertThat(body.get("vin").asString()).isEqualTo(vehicle.body().get("vin"));
        assertThat(body.get("renavam").asString()).isEqualTo(vehicle.body().get("renavam"));
        assertThat(body.get("color").asString()).isEqualTo("Prata");
        assertThat(body.get("manufactureYear").asInt()).isEqualTo(2023);
        assertThat(body.get("modelYear").asInt()).isEqualTo(2024);
        assertThat(body.get("soldPriceCents").asLong()).isEqualTo(8_990_000L);
    }

    @Test
    void readingDoesNotChangeNorAuditTheVehicle() {
        Registered vehicle = register();

        sales().get(path(vehicle.id()));

        assertThat(row(vehicle.id())).containsEntry("version", 0);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_log WHERE entity_id = :id").param("id", vehicle.id())
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void anUnknownVehicleIsNotFound() {
        ResponseEntity<JsonNode> response = sales().get(path(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void anIdThatIsNotAUuidIsRefused() {
        ResponseEntity<JsonNode> response = sales().get("/internal/vehicles/abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactly("id TYPE_INVALID");
    }

    @Test
    void theReservationScopeDoesNotRead() {
        Registered vehicle = register();

        ResponseEntity<JsonNode> response = api().asService(TestTokens.SALES_CLIENT, "vehicles.reserve")
                .get(path(vehicle.id()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void peopleCannotReadEvenTheStaff() {
        Registered vehicle = register();

        assertThat(api().asSeller(SELLER).get(path(vehicle.id())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().asAdmin(ADMIN).get(path(vehicle.id())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().asCustomer(CUSTOMER).get(path(vehicle.id())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void withoutATokenTheAnswerIsUnauthenticated() {
        Registered vehicle = register();

        assertThat(api().get(path(vehicle.id())).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ApiDriver sales() {
        return api().asService(TestTokens.SALES_CLIENT, "vehicles.read");
    }

    private static String path(UUID id) {
        return "/internal/vehicles/" + id;
    }
}
