package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** O contrato que o front integra, gerado do código e publicado sem token. */
class CustomerApiDocsTests extends CustomersApiTest {

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

        assertThat(operations).containsExactlyInAnyOrder("POST /customers", "GET /customers/me", "PATCH /customers/me",
                "GET /customers/{id}", "POST /customers/{id}/cpf-reveal");
        assertThat(docs.at("/info/title").asString()).isEqualTo("Cadastro de compradores");
    }

    @Test
    void theStaffSeesTheCustomerMaskedAndOnlyTheAdminRevealsTheCpf() {
        JsonNode view = operation("/customers/{id}", "get");
        JsonNode reveal = operation("/customers/{id}/cpf-reveal", "post");

        assertThat(view.get("description").asString()).contains("UC-14", "Papéis: SELLER");
        assertThat(view.get("responses").propertyNames()).contains("200", "400", "401", "403", "404", "500");
        assertThat(view.at("/responses/200/content/application~1json/schema/$ref").asString())
                .endsWith("/StaffCustomerResponse");
        assertThat(reveal.get("description").asString()).contains("UC-14", "CUSTOMER_CPF_REVEALED", "Papéis: ADMIN")
                .doesNotContain("SELLER");
        assertThat(reveal.get("responses").propertyNames()).contains("200", "400", "401", "403", "404", "500");
        assertThat(reveal.at("/responses/400/description").asString()).contains("REQUIRED", "SIZE_INVALID");
        assertThat(reveal.get("parameters").valueStream().map(parameter -> parameter.get("name").asString()))
                .doesNotContain("Idempotency-Key");
        assertThat(docs.at("/components/schemas/StaffCustomerResponse/properties").propertyNames())
                .contains("cpfMasked", "emailMasked", "phoneMasked", "city", "state")
                .doesNotContain("cpf", "email", "phone", "birthDate", "address", "identitySubject");
        assertThat(docs.at("/components/schemas/CpfRevealRequest/required").valueStream().map(JsonNode::asString))
                .containsExactly("reason");
    }

    @Test
    void theOwnRecordIsSeenAndEditedOnlyByCustomersWithoutIdempotencyKey() {
        JsonNode view = operation("/customers/me", "get");
        JsonNode edit = operation("/customers/me", "patch");

        for (JsonNode own : List.of(view, edit)) {
            assertThat(own.get("description").asString()).contains("UC-07", "Papéis: CUSTOMER");
            assertThat(own.get("security").toString()).isEqualTo("[{\"oidc\":[]},{\"bearer\":[]}]");
            assertThat(own.at("/responses/404/description").asString()).contains("NOT_FOUND");
            assertThat(own.at("/responses/200/content/application~1json/schema/$ref").asString())
                    .endsWith("/CustomerResponse");
            assertThat(own.get("parameters").valueStream().map(parameter -> parameter.get("name").asString()))
                    .doesNotContain("Idempotency-Key");
        }
        assertThat(view.get("responses").propertyNames()).contains("200", "401", "403", "404", "500")
                .doesNotContain("400", "429");
        assertThat(edit.get("responses").propertyNames()).contains("200", "400", "401", "403", "404", "500")
                .doesNotContain("429");
        assertThat(edit.at("/responses/400/description").asString()).contains("NOT_EDITABLE", "REQUIRED");
    }

    @Test
    void theEditTakesOnlyNamePhoneAndAddressAllOptional() {
        JsonNode changes = docs.at("/components/schemas/CustomerChangesRequest");

        assertThat(changes.get("properties").propertyNames()).containsExactlyInAnyOrder("fullName", "phone", "address");
        assertThat(changes.get("required")).isNull();
    }

    @Test
    void theRegistrationIsForCustomersAndListsItsErrorsAndTheRateLimit() {
        JsonNode registration = operation("/customers", "post");

        assertThat(registration.get("description").asString()).contains("Papéis: CUSTOMER");
        assertThat(registration.get("security").toString()).isEqualTo("[{\"oidc\":[]},{\"bearer\":[]}]");
        assertThat(registration.get("responses").propertyNames())
                .contains("201", "400", "401", "403", "409", "422", "428", "429", "500");
        assertThat(registration.at("/responses/409/description").asString())
                .contains("CUSTOMER_ALREADY_REGISTERED", "CPF_ALREADY_REGISTERED");
        assertThat(registration.at("/responses/422/description").asString()).contains("EMAIL_NOT_VERIFIED");
        JsonNode key = registration.get("parameters").valueStream()
                .filter(parameter -> parameter.get("name").asString().equals("Idempotency-Key"))
                .findFirst().orElseThrow();
        assertThat(key.get("in").asString()).isEqualTo("header");
        assertThat(key.get("required").asBoolean()).isTrue();
    }

    @Test
    void theRequestHasNoEmailAndTheResponseOnlyMaskedData() {
        assertThat(docs.at("/components/schemas/CustomerRequest/required").valueStream().map(JsonNode::asString))
                .containsExactlyInAnyOrder("fullName", "cpf", "birthDate", "phone", "address");
        assertThat(docs.at("/components/schemas/CustomerRequest/properties").propertyNames()).doesNotContain("email");
        assertThat(docs.at("/components/schemas/CustomerResponse/properties").propertyNames())
                .contains("cpfMasked", "emailMasked", "phoneMasked")
                .doesNotContain("cpf", "email", "phone", "identitySubject");
        assertThat(docs.at("/components/schemas/AddressBody/required").valueStream().map(JsonNode::asString))
                .doesNotContain("complement");
    }

    @Test
    void everyErrorAnswersInTheApiErrorFormatAndEveryOperationTakesTheRequestId() {
        docs.get("paths").forEach(path -> path.forEach(operation -> {
            operation.get("responses").properties().forEach(response -> {
                if (response.getKey().charAt(0) >= '4') {
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
