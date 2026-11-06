package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** UC-13: acompanhar compras — o dono vê as próprias, a equipe vê todas, outro cliente recebe {@code 404}. */
class PurchaseQueryTests extends SalesApiTest {

    @Test
    void theOwnerSeesThePurchaseWithTheWholeTimeline() {
        String subject = newCustomer();
        UUID id = awaitingPurchase(subject);
        pay(id);

        ResponseEntity<JsonNode> seen = customer(subject).get(PURCHASES + "/" + id);

        assertThat(seen.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode purchase = seen.getBody();
        assertThat(purchase.get("status").asString()).isEqualTo("PAID");
        assertThat(purchase.get("payment").get("paidAmountCents").asLong()).isEqualTo(FakeNeighbors.DEFAULT_PRICE_CENTS);
        assertThat(purchase.get("payment").get("paidAt").asString()).isEqualTo("2026-11-06T14:10:00Z");
        assertThat(purchase.get("timeline").valueStream().map(step -> step.get("status").asString()).toList())
                .containsExactly("PENDING", "RESERVED", "AWAITING_PAYMENT", "PAID");
        assertThat(purchase.get("timeline").get(3).get("reason").asString()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(purchase.toString()).doesNotContain(subject);
    }

    @Test
    void anotherCustomerGetsNotFoundAndTheTeamSeesAnyPurchase() {
        UUID id = awaitingPurchase(newCustomer());
        String path = PURCHASES + "/" + id;

        assertThat(customer(newCustomer()).get(path).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(customer(newCustomer()).get(PURCHASES + "/" + UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api().asSeller(newCustomer()).get(path).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api().asAdmin(newCustomer()).get(path).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api().asSeller(newCustomer()).get(PURCHASES + "/" + UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api().asService("customers-service", "sales.read-retention").get(path).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().anonymous().get(path).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<JsonNode> notAnId = api().asSeller(newCustomer()).get(PURCHASES + "/compra");
        assertThat(notAnId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notAnId.getBody().get("fields").get(0).get("code").asString()).isEqualTo("TYPE_INVALID");
    }

    @Test
    void theCustomerListsOnlyTheirOwnMostRecentFirst() {
        String subject = newCustomer();
        UUID first = awaitingPurchase(subject);
        customer(subject).post(PURCHASES + "/" + first + "/cancellation", null);
        UUID second = awaitingPurchase(subject);
        awaitingPurchase(newCustomer());

        ResponseEntity<JsonNode> page = customer(subject).get(PURCHASES + "?page=0&size=10");

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idsOf(page)).containsExactly(second.toString(), first.toString());
        assertThat(page.getBody().get("totalItems").asLong()).isEqualTo(2);
        assertThat(page.getBody().get("items").get(0).has("timeline")).isFalse();
        assertThat(idsOf(customer(subject).get(PURCHASES + "?status=CANCELLED"))).containsExactly(first.toString());
        assertThat(idsOf(customer(subject).get(PURCHASES + "?page=1&size=1"))).containsExactly(first.toString());
    }

    @Test
    void theTeamListsEveryoneAndFiltersByStatus() {
        UUID mine = awaitingPurchase(newCustomer());
        UUID theirs = awaitingPurchase(newCustomer());

        ResponseEntity<JsonNode> awaiting = api().asSeller(newCustomer())
                .get(PURCHASES + "?status=AWAITING_PAYMENT&size=100");
        ResponseEntity<JsonNode> paid = api().asAdmin(newCustomer()).get(PURCHASES + "?status=PAID&size=100");

        assertThat(awaiting.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(awaiting.getBody().get("items").valueStream().map(item -> item.get("status").asString()))
                .containsOnly("AWAITING_PAYMENT");
        if (awaiting.getBody().get("totalItems").asLong() <= 100) {
            assertThat(idsOf(awaiting)).contains(mine.toString(), theirs.toString());
        }
        assertThat(paid.getBody().get("items").valueStream().map(item -> item.get("status").asString()))
                .containsOnly("PAID");
    }

    @Test
    void aBadPageOrStatusIsRefused() {
        for (String query : List.of("?size=0", "?size=101", "?page=-1", "?status=PAGA")) {
            ResponseEntity<JsonNode> refused = customer(newCustomer()).get(PURCHASES + query);
            assertThat(refused.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refused.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        }
        assertThat(api().anonymous().get(PURCHASES).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(api().asService("customers-service", "sales.read-retention").get(PURCHASES).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static List<String> idsOf(ResponseEntity<JsonNode> page) {
        return page.getBody().get("items").valueStream().map(item -> item.get("id").asString()).toList();
    }
}
