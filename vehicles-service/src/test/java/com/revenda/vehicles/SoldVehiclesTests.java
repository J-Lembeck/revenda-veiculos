package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class SoldVehiclesTests extends VehiclesApiTest {

    @Test
    void theSellerSeesSoldAndDeliveredBySalePriceWithTheIdBreakingTies() {
        Registered delivered = register();
        Registered sold = register();
        Registered tiedSold = register();
        Registered available = register();
        Registered reserved = register();
        markSold(delivered.id(), 3, true);
        markSold(sold.id(), 1, false);
        markSold(tiedSold.id(), 1, false);
        markReserved(reserved.id());

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).get("/vehicles/sold?size=100");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = idsOf(response);
        List<UUID> tied = sold.id().toString().compareTo(tiedSold.id().toString()) < 0
                ? List.of(sold.id(), tiedSold.id()) : List.of(tiedSold.id(), sold.id());
        assertThat(ids.subList(0, 2)).containsExactlyElementsOf(tied);
        assertThat(ids).contains(delivered.id()).doesNotContain(available.id(), reserved.id());
        assertThat(ids.indexOf(delivered.id())).isGreaterThan(1);
        List<Long> salePrices = new ArrayList<>();
        response.getBody().get("items").forEach(item -> {
            assertThat(item.get("status").asString()).isIn("SOLD", "DELIVERED");
            salePrices.add(item.get("soldPriceCents").asLong());
        });
        assertThat(salePrices).isSorted();
        assertThat(response.getBody().get("totalItems").asLong()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void theSoldListShowsTheIdentifiersAndTheSale() {
        Registered vehicle = register();
        markSold(vehicle.id(), 2, true);

        JsonNode item = api().asAdmin(ADMIN).get("/vehicles/sold?size=100").getBody().get("items").valueStream()
                .filter(each -> each.get("id").asString().equals(vehicle.id().toString())).findFirst().orElseThrow();

        assertThat(item.get("plate").asString()).isEqualTo(vehicle.body().get("plate"));
        assertThat(item.get("soldPriceCents").asLong()).isEqualTo(2);
        assertThat(item.get("status").asString()).isEqualTo("DELIVERED");
        assertThat(item.get("deliveredAt").isNull()).isFalse();
    }

    @Test
    void aPageOutOfTheLimitsIsRefusedWithBothFields() {
        ResponseEntity<JsonNode> response = api().asSeller(SELLER).get("/vehicles/sold?page=-1&size=101");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactlyInAnyOrder("page OUT_OF_RANGE", "size OUT_OF_RANGE");
    }

    @Test
    void aCustomerCannotSeeTheSoldList() {
        ResponseEntity<JsonNode> response = api().asCustomer(CUSTOMER).get("/vehicles/sold");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void withoutATokenTheAnswerIsUnauthenticated() {
        ResponseEntity<JsonNode> response = api().get("/vehicles/sold");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }
}
