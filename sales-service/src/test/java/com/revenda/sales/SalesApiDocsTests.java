package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** O contrato das compras que o front integra, gerado do código e publicado sem token. */
class SalesApiDocsTests extends SalesApiTest {

    private JsonNode docs;

    @BeforeEach
    void readTheContract() {
        ResponseEntity<JsonNode> response = api().get("/v3/api-docs");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        docs = response.getBody();
    }

    @Test
    void theContractListsEveryRouteOfTheServiceAndNothingElse() {
        List<String> operations = new ArrayList<>();
        docs.get("paths").properties().forEach(path -> path.getValue().properties()
                .forEach(method -> operations.add(method.getKey().toUpperCase() + " " + path.getKey())));

        assertThat(operations).containsExactlyInAnyOrder("POST /purchases", "GET /purchases", "GET /purchases/{id}",
                "POST /purchases/{id}/cancellation", "POST /webhooks/payments");
        assertThat(docs.at("/info/title").asString()).isEqualTo("Compras");
    }

    @Test
    void thePurchaseIsForCustomersWithTheKeyItsErrorsAndTheRateLimit() {
        JsonNode create = operation("/purchases", "post");

        assertThat(create.get("description").asString()).contains("UC-08", "RN-09", "RN-10", "Papéis: CUSTOMER",
                "PURCHASE_CREATED");
        assertThat(create.get("responses").propertyNames())
                .contains("201", "400", "401", "403", "409", "422", "428", "429", "500", "503");
        assertThat(create.at("/responses/409/description").asString()).contains("VEHICLE_UNAVAILABLE",
                "ACTIVE_PURCHASE_EXISTS", "PURCHASE_IN_PROGRESS");
        assertThat(create.at("/responses/422/description").asString()).contains("CUSTOMER_PROFILE_REQUIRED",
                "CUSTOMER_UNDERAGE", "IDEMPOTENCY_KEY_REUSED");
        assertThat(create.at("/responses/503/description").asString()).contains("DEPENDENCY_UNAVAILABLE");
        JsonNode key = create.get("parameters").valueStream()
                .filter(parameter -> parameter.get("name").asString().equals("Idempotency-Key"))
                .findFirst().orElseThrow();
        assertThat(key.get("in").asString()).isEqualTo("header");
        assertThat(key.get("required").asBoolean()).isTrue();
        assertThat(docs.at("/components/schemas/PurchaseRequest/properties").propertyNames())
                .containsExactly("vehicleId");
    }

    @Test
    void theAnswerHasThePaymentAndTheTimelineAndNothingOfTheCustomerButTheId() {
        assertThat(docs.at("/components/schemas/PurchaseResponse/properties").propertyNames())
                .containsExactlyInAnyOrder("id", "customerId", "vehicleId", "status", "closedReason", "priceCents",
                        "payment", "createdAt", "updatedAt", "timeline");
        assertThat(docs.at("/components/schemas/PurchaseSummaryResponse/properties").propertyNames())
                .doesNotContain("timeline", "customerSubject");
        assertThat(docs.at("/components/schemas/PaymentResponse/properties").propertyNames())
                .containsExactlyInAnyOrder("status", "code", "dueAt", "paidAt", "paidAmountCents");
        assertThat(docs.at("/components/schemas/Step/properties").propertyNames())
                .containsExactlyInAnyOrder("status", "paymentStatus", "reason", "at");
    }

    @Test
    void theQueriesAreForTheOwnerAndTheTeamAndTheCancellationOnlyForTheOwner() {
        JsonNode list = operation("/purchases", "get");
        JsonNode view = operation("/purchases/{id}", "get");
        JsonNode cancel = operation("/purchases/{id}/cancellation", "post");

        assertThat(list.get("description").asString()).contains("UC-13", "CUSTOMER", "SELLER");
        assertThat(view.get("description").asString()).contains("UC-13", "RN-15");
        assertThat(view.get("responses").propertyNames()).contains("200", "400", "401", "403", "404", "500");
        assertThat(cancel.get("description").asString()).contains("UC-11", "RN-13", "Papéis: CUSTOMER",
                "PURCHASE_CANCELLED").doesNotContain("SELLER");
        assertThat(cancel.at("/responses/409/description").asString()).contains("PURCHASE_IN_PROGRESS",
                "PURCHASE_NOT_CANCELLABLE");
        assertThat(cancel.get("parameters").valueStream().map(parameter -> parameter.get("name").asString()))
                .doesNotContain("Idempotency-Key");
    }

    @Test
    void theWebhookIsOpenSignedAndDocumentsItsAnswers() {
        JsonNode webhook = operation("/webhooks/payments", "post");

        assertThat(webhook.get("description").asString()).contains("UC-09", "HMAC-SHA256", "PAYMENT_WEBHOOK_REJECTED");
        assertThat(webhook.get("responses").propertyNames()).contains("200", "400", "401", "409");
        assertThat(webhook.at("/responses/401/description").asString()).contains("INVALID_SIGNATURE");
        JsonNode signature = webhook.get("parameters").valueStream()
                .filter(parameter -> parameter.get("name").asString().equals("X-Signature"))
                .findFirst().orElseThrow();
        assertThat(signature.get("required").asBoolean()).isTrue();
        assertThat(docs.at("/components/schemas/PaymentWebhookBody/properties").propertyNames())
                .containsExactlyInAnyOrder("eventId", "type", "chargeId", "referenceId", "amountCents", "occurredAt");
    }

    @Test
    void everyErrorAnswersInTheApiErrorFormatAndEveryOperationTakesTheRequestId() {
        docs.get("paths").forEach(path -> path.forEach(operation -> {
            operation.get("responses").properties().forEach(response -> {
                if (response.getKey().charAt(0) >= '4' && response.getValue().has("content")) {
                    assertThat(response.getValue().at("/content/application~1json/schema/$ref").asString())
                            .as(response.getKey()).endsWith("/ApiError");
                }
            });
            assertThat(operation.get("parameters").valueStream().map(parameter -> parameter.get("name").asString()))
                    .contains("X-Request-Id");
        }));
    }

    @Test
    void theSwaggerUiOpensWithoutAToken() {
        ResponseEntity<String> page = api().exchange(HttpMethod.GET, "/swagger-ui/index.html", null, String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("swagger-ui");
    }

    private JsonNode operation(String path, String method) {
        return docs.get("paths").get(path).get(method);
    }
}
