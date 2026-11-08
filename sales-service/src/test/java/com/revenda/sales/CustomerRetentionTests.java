package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.TestTokens;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchasePaths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Até quando guardar os dados de um cliente, pelo que as compras dele pedem (LGPD). */
class CustomerRetentionTests extends SalesApiTest {

    @Test
    void aCustomerWithoutPurchasesHasNothingToRetain() {
        ResponseEntity<JsonNode> answer = retentionOf(UUID.randomUUID());

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getBody().get("retainUntil").isNull()).isTrue();
    }

    @Test
    void closedPurchasesWithoutSaleRetainNothing() {
        UUID customer = UUID.randomUUID();
        for (PurchasePaths.Path path : PurchasePaths.closedUnpaid()) {
            moveTo(customer, persist(path));
        }
        moveTo(customer, persist(PurchasePaths.refunded(PurchasePaths.cancelledAfterPaying())));

        assertThat(retentionOf(customer).getBody().get("retainUntil").isNull()).isTrue();
    }

    @Test
    void aSaleIsRetainedFiveYearsAfterThePickup() {
        Purchase sale = persist(PurchasePaths.documented());

        assertThat(retainUntil(sale.customerId()))
                .isEqualTo(sale.completedAt().atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
    }

    @Test
    void anOpenPurchaseIsRetainedAsIfItWereCompletedNow() {
        Purchase open = persist(PurchasePaths.paid());
        Instant before = clock.instant();

        Instant until = retainUntil(open.customerId());

        assertThat(until).isBetween(before.atOffset(ZoneOffset.UTC).plusYears(5).toInstant(),
                clock.instant().atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
    }

    @Test
    void anotherCustomersSaleDoesNotCount() {
        persist(PurchasePaths.completed());

        assertThat(retentionOf(UUID.randomUUID()).getBody().get("retainUntil").isNull()).isTrue();
    }

    @Test
    void anIdThatIsNotAUuidIsRefused() {
        ResponseEntity<JsonNode> answer = customersService().get("/internal/customers/cliente/retention");

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.getBody().get("fields").get(0).get("code").asString()).isEqualTo("TYPE_INVALID");
    }

    private Instant retainUntil(UUID customerId) {
        return Instant.parse(retentionOf(customerId).getBody().get("retainUntil").asString());
    }

    private ResponseEntity<JsonNode> retentionOf(UUID customerId) {
        return customersService().get("/internal/customers/" + customerId + "/retention");
    }

    private ApiDriver customersService() {
        return api().asService(TestTokens.CUSTOMERS_CLIENT, "sales.read-retention");
    }

    private void moveTo(UUID customer, Purchase purchase) {
        jdbc.sql("UPDATE purchase SET customer_id = :customer WHERE id = :id")
                .param("customer", customer).param("id", purchase.id()).update();
    }
}
