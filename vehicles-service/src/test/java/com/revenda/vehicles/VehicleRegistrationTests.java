package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.test.ApiDriver;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class VehicleRegistrationTests extends VehiclesApiTest {

    @Test
    void theSellerRegistersAnAvailableVehicleAndSeesItsIdentifiers() {
        Map<String, Object> body = vehicleBody();

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).post("/vehicles", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode vehicle = response.getBody();
        assertThat(vehicle.get("status").asString()).isEqualTo("AVAILABLE");
        assertThat(vehicle.get("version").asInt()).isZero();
        assertThat(vehicle.get("priceCents").asLong()).isEqualTo(8_990_000L);
        assertThat(vehicle.get("plate").asString()).isEqualTo(body.get("plate"));
        assertThat(vehicle.get("vin").asString()).isEqualTo(body.get("vin"));
        assertThat(vehicle.get("renavam").asString()).isEqualTo(body.get("renavam"));
        assertThat(vehicle.get("trimName").asString()).isEqualTo("1.0 Turbo LT");
        assertThat(vehicle.get("soldPriceCents").isNull()).isTrue();
        String id = vehicle.get("id").asString();
        assertThat(response.getHeaders().getLocation()).hasToString("/vehicles/" + id);
        assertThat(jdbc.sql("SELECT status FROM vehicle WHERE id = :id").param("id", UUID.fromString(id))
                .query(String.class).single()).isEqualTo("AVAILABLE");
    }

    @Test
    void theRegistrationIsAuditedWithTheSellerAndTheVehicleAsItWasCreated() {
        Map<String, Object> body = vehicleBody();

        String id = api().asSeller(SELLER).post("/vehicles", body).getBody().get("id").asString();

        Map<String, Object> audit = jdbc.sql("""
                        SELECT actor_type, actor_id, actor_role, summary, data ->> 'before' AS before,
                               data -> 'after' ->> 'plate' AS plate, data -> 'after' ->> 'priceCents' AS price
                          FROM audit_log WHERE action = 'VEHICLE_CREATED' AND entity_id = :id""")
                .param("id", UUID.fromString(id))
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_id", SELLER)
                .containsEntry("actor_role", "SELLER").containsEntry("summary", "Veículo cadastrado: Chevrolet Onix 2023/2024")
                .containsEntry("plate", body.get("plate")).containsEntry("price", "8990000");
        assertThat(audit.get("before")).isNull();
    }

    @Test
    void theAdminRegistersToo() {
        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).post("/vehicles", vehicleBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void theTrimNameIsOptional() {
        Map<String, Object> body = vehicleBody();
        body.remove("trimName");

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).post("/vehicles", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("trimName").isNull()).isTrue();
    }

    @Test
    void aCustomerCannotRegisterAndNothingIsSaved() {
        Map<String, Object> body = vehicleBody();

        ResponseEntity<JsonNode> response = api().asCustomer(CUSTOMER).post("/vehicles", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(vehiclesWithPlate(body.get("plate"))).isZero();
    }

    @Test
    void withoutATokenTheAnswerIsUnauthenticated() {
        ResponseEntity<JsonNode> response = api().post("/vehicles", vehicleBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void withoutTheIdempotencyKeyNothingIsCreated() {
        Map<String, Object> body = vehicleBody();

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).withoutIdempotencyKey().post("/vehicles", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(vehiclesWithPlate(body.get("plate"))).isZero();
    }

    @Test
    void repeatingWithTheSameKeyReturnsTheSameVehicleWithoutCreatingAnother() {
        ApiDriver seller = api().asSeller(SELLER).withIdempotencyKey(UUID.randomUUID().toString());
        Map<String, Object> body = vehicleBody();

        ResponseEntity<JsonNode> first = seller.post("/vehicles", body);
        ResponseEntity<JsonNode> second = seller.post("/vehicles", body);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(vehiclesWithPlate(body.get("plate"))).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_log WHERE entity_id = :id")
                .param("id", UUID.fromString(first.getBody().get("id").asString())).query(Integer.class).single())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plate", "vin", "renavam"})
    void aRepeatedIdentifierIsRefusedAndNothingIsSavedNorAudited(String identifier) {
        Map<String, Object> first = vehicleBody();
        api().asSeller(SELLER).post("/vehicles", first);
        Map<String, Object> second = vehicleBody(Map.of(identifier, first.get(identifier)));
        int auditBefore = auditRows();

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).post("/vehicles", second);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString())
                .isEqualTo(identifier.toUpperCase() + "_ALREADY_REGISTERED");
        assertThat(response.getBody().get("message").asString()).startsWith("Já existe um veículo com est");
        String otherIdentifier = identifier.equals("plate") ? "vin" : "plate";
        assertThat(jdbc.sql("SELECT count(*) FROM vehicle WHERE " + otherIdentifier + " = :value")
                .param("value", second.get(otherIdentifier)).query(Integer.class).single()).isZero();
        assertThat(auditRows()).isEqualTo(auditBefore);
    }

    static Stream<Arguments> invalidFields() {
        int nextYear = Year.now(ZoneOffset.UTC).getValue() + 1;
        return Stream.of(
                arguments("plate", "ABC-1234", "PLATE_INVALID"),
                arguments("plate", "bra2e19", "PLATE_INVALID"),
                arguments("vin", "9BGEA48I0PG000001", "VIN_INVALID"),
                arguments("vin", "9BGEA48A0PG00001", "VIN_INVALID"),
                arguments("renavam", "00639884961", "RENAVAM_INVALID"),
                arguments("manufactureYear", 1899, "YEAR_INVALID"),
                arguments("manufactureYear", nextYear + 1, "YEAR_INVALID"),
                arguments("modelYear", 2025, "YEAR_INVALID"),
                arguments("mileageKm", -1, "OUT_OF_RANGE"),
                arguments("priceCents", 0, "PRICE_INVALID"),
                arguments("priceCents", 1_000_000_001L, "PRICE_INVALID"),
                arguments("priceCents", "caro", "TYPE_INVALID"),
                arguments("brand", "", "REQUIRED"),
                arguments("brand", "M".repeat(61), "SIZE_INVALID"),
                arguments("color", null, "REQUIRED"),
                arguments("trimName", "V".repeat(81), "SIZE_INVALID"));
    }

    @ParameterizedTest(name = "{0} = {1} → {2}")
    @MethodSource("invalidFields")
    void anInvalidFieldIsRefusedWithItsCodeAndNothingIsSaved(String field, Object value, String code) {
        Map<String, Object> changes = new HashMap<>();
        changes.put(field, value);
        Map<String, Object> body = vehicleBody(changes);

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).post("/vehicles", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldsOf(response)).contains(field + " " + code);
        assertThat(vehiclesWithPlate(body.get("plate"))).isZero();
    }

    @Test
    void everyBrokenRuleComesBackInOneAnswer() {
        Map<String, Object> body = vehicleBody(Map.of("plate", "ABC-1234", "vin", "CURTO", "priceCents", 0));

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).post("/vehicles", body);

        assertThat(fieldsOf(response)).containsExactlyInAnyOrder(
                "plate PLATE_INVALID", "vin VIN_INVALID", "priceCents PRICE_INVALID");
    }

    private int vehiclesWithPlate(Object plate) {
        return jdbc.sql("SELECT count(*) FROM vehicle WHERE plate = :plate").param("plate", plate)
                .query(Integer.class).single();
    }

    private int auditRows() {
        return jdbc.sql("SELECT count(*) FROM audit_log").query(Integer.class).single();
    }
}
