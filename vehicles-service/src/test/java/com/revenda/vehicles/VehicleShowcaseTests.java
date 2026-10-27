package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class VehicleShowcaseTests extends VehiclesApiTest {

    @Test
    void theVisitorSeesOnlyVehiclesForSaleFromTheCheapestWithTheIdBreakingTies() {
        String brand = uniqueBrand();
        Registered expensive = register(Map.of("brand", brand, "priceCents", 5_000_000L));
        Registered cheap = register(Map.of("brand", brand, "priceCents", 3_000_000L));
        Registered sameCheapPrice = register(Map.of("brand", brand, "priceCents", 3_000_000L));
        Registered reserved = register(Map.of("brand", brand, "priceCents", 1_000_000L));
        Registered sold = register(Map.of("brand", brand, "priceCents", 2_000_000L));
        markReserved(reserved.id());
        markSold(sold.id(), 2_000_000L, false);

        ResponseEntity<JsonNode> response = api().get("/vehicles?brand=" + brand);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> tied = Stream.of(cheap.id(), sameCheapPrice.id()).sorted(Comparator.comparing(UUID::toString)).toList();
        assertThat(idsOf(response)).containsExactly(tied.get(0), tied.get(1), expensive.id());
        JsonNode page = response.getBody();
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalItems").asLong()).isEqualTo(3);
    }

    @Test
    void theShowcaseHidesPlateVinAndRenavam() {
        String brand = uniqueBrand();
        register(Map.of("brand", brand));

        JsonNode item = api().get("/vehicles?brand=" + brand).getBody().get("items").get(0);

        assertThat(item.has("plate")).isFalse();
        assertThat(item.has("vin")).isFalse();
        assertThat(item.has("renavam")).isFalse();
        assertThat(item.has("version")).isFalse();
        assertThat(item.get("brand").asString()).isEqualTo(brand);
        assertThat(item.get("priceCents").asLong()).isEqualTo(8_990_000L);
        assertThat(item.get("modelYear").asInt()).isEqualTo(2024);
    }

    @Test
    void customersAndStaffSeeTheSamePublicShowcase() {
        String brand = uniqueBrand();
        register(Map.of("brand", brand));

        JsonNode asCustomer = api().asCustomer(CUSTOMER).get("/vehicles?brand=" + brand).getBody();
        JsonNode asSeller = api().asSeller(SELLER).get("/vehicles?brand=" + brand).getBody();

        assertThat(asCustomer).isEqualTo(asSeller);
        assertThat(asSeller.get("items").get(0).has("plate")).isFalse();
    }

    @Test
    void pagesWalkTheSameOrderAndKeepTheTotal() {
        String brand = uniqueBrand();
        register(Map.of("brand", brand, "priceCents", 3_000_000L));
        Registered middle = register(Map.of("brand", brand, "priceCents", 4_000_000L));
        register(Map.of("brand", brand, "priceCents", 5_000_000L));

        ResponseEntity<JsonNode> second = api().get("/vehicles?brand=" + brand + "&page=1&size=1");
        ResponseEntity<JsonNode> beyond = api().get("/vehicles?brand=" + brand + "&page=5&size=1");

        assertThat(idsOf(second)).containsExactly(middle.id());
        assertThat(second.getBody().get("page").asInt()).isEqualTo(1);
        assertThat(second.getBody().get("size").asInt()).isEqualTo(1);
        assertThat(second.getBody().get("totalItems").asLong()).isEqualTo(3);
        assertThat(idsOf(beyond)).isEmpty();
        assertThat(beyond.getBody().get("totalItems").asLong()).isEqualTo(3);
    }

    @Test
    void brandAndModelMatchWholeIgnoringCase() {
        String brand = uniqueBrand();
        Registered onix = register(Map.of("brand", brand, "model", "Onix"));
        register(Map.of("brand", brand, "model", "Onix Plus"));

        ResponseEntity<JsonNode> response = api().get("/vehicles?brand=" + brand.toUpperCase() + "&model=onix");

        assertThat(idsOf(response)).containsExactly(onix.id());
    }

    @Test
    void thePriceRangeIncludesBothEnds() {
        String brand = uniqueBrand();
        register(Map.of("brand", brand, "priceCents", 2_999_999L));
        Registered low = register(Map.of("brand", brand, "priceCents", 3_000_000L));
        Registered high = register(Map.of("brand", brand, "priceCents", 4_000_000L));
        register(Map.of("brand", brand, "priceCents", 4_000_001L));

        ResponseEntity<JsonNode> response = api()
                .get("/vehicles?brand=" + brand + "&minPriceCents=3000000&maxPriceCents=4000000");

        assertThat(idsOf(response)).containsExactly(low.id(), high.id());
    }

    @Test
    void theYearRangeIsAboutTheModelYear() {
        String brand = uniqueBrand();
        Registered model2021 = register(Map.of("brand", brand, "manufactureYear", 2020, "modelYear", 2021));
        register(Map.of("brand", brand, "manufactureYear", 2019, "modelYear", 2020));
        register(Map.of("brand", brand, "manufactureYear", 2022, "modelYear", 2023));

        ResponseEntity<JsonNode> response = api().get("/vehicles?brand=" + brand + "&minYear=2021&maxYear=2022");

        assertThat(idsOf(response)).containsExactly(model2021.id());
    }

    @ParameterizedTest(name = "{0} → {1} {2}")
    @CsvSource({
            "size=101, size, OUT_OF_RANGE",
            "size=0, size, OUT_OF_RANGE",
            "page=-1, page, OUT_OF_RANGE",
            "size=muitos, size, TYPE_INVALID",
            "minPriceCents=-1, minPriceCents, OUT_OF_RANGE",
            "maxYear=dois-mil, maxYear, TYPE_INVALID"})
    void anInvalidQueryIsRefusedWithTheField(String query, String field, String code) {
        ResponseEntity<JsonNode> response = api().get("/vehicles?" + query);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldsOf(response)).containsExactly(field + " " + code);
    }

    @Test
    void aBadTokenOnTheOpenShowcaseIsRefusedInsteadOfBecomingAVisitor() {
        ResponseEntity<JsonNode> response = api().as("nao-e-um-jwt").get("/vehicles");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    private static String uniqueBrand() {
        return "Marca" + UUID.randomUUID().toString().substring(0, 8);
    }
}
