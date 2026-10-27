package com.revenda.vehicles;

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
class VehicleApiDocsTests extends VehiclesApiTest {

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

        assertThat(operations).containsExactlyInAnyOrder(
                "POST /vehicles", "GET /vehicles", "PUT /vehicles/{id}", "GET /vehicles/{id}", "GET /vehicles/sold",
                "POST /internal/vehicles/{id}/reservations");
        assertThat(docs.at("/info/title").asString()).isEqualTo("Estoque de veículos");
    }

    @Test
    void openRoutesNeedNoTokenAndSayThatABadOneIsRefused() {
        JsonNode showcase = operation("/vehicles", "get");

        assertThat(showcase.get("security").toString()).isEqualTo("[{\"oidc\":[]},{\"bearer\":[]},{}]");
        assertThat(showcase.get("description").asString()).contains("**Acesso:** Aberta");
        assertThat(showcase.at("/responses/401").isMissingNode()).isFalse();
        assertThat(showcase.at("/responses/403").isMissingNode()).isTrue();
        assertThat(queryParameters(showcase)).contains("brand", "model", "minPriceCents", "maxPriceCents", "minYear",
                "maxYear", "page", "size");
    }

    @Test
    void staffRoutesNameTheRoleAndOfferTheLoginOrAToken() {
        JsonNode sold = operation("/vehicles/sold", "get");

        assertThat(sold.get("description").asString()).contains("Papéis: SELLER (o ADMIN herda o SELLER)");
        assertThat(sold.get("security").toString()).isEqualTo("[{\"oidc\":[]},{\"bearer\":[]}]");
        assertThat(sold.at("/responses/403/description").asString()).contains("FORBIDDEN");
        assertThat(docs.at("/components/securitySchemes/oidc/flows/authorizationCode/authorizationUrl").asString())
                .isEqualTo("http://localhost:8180/realms/revenda/protocol/openid-connect/auth");
    }

    @Test
    void theInternalRouteTakesOnlyAServiceTokenWithTheScope() {
        JsonNode reservation = operation("/internal/vehicles/{id}/reservations", "post");

        assertThat(reservation.get("security").toString()).isEqualTo("[{\"bearer\":[]}]");
        assertThat(reservation.get("description").asString()).contains("scope `vehicles.reserve`");
        assertThat(reservation.at("/responses/409/description").asString()).contains("VEHICLE_UNAVAILABLE");
    }

    @Test
    void theRegistrationAsksForTheIdempotencyKeyAndListsItsErrors() {
        JsonNode registration = operation("/vehicles", "post");

        JsonNode key = registration.get("parameters").valueStream()
                .filter(parameter -> parameter.get("name").asString().equals("Idempotency-Key"))
                .findFirst().orElseThrow();
        assertThat(key.get("in").asString()).isEqualTo("header");
        assertThat(key.get("required").asBoolean()).isTrue();
        assertThat(registration.get("responses").propertyNames())
                .contains("201", "400", "401", "403", "409", "422", "428", "500");
        assertThat(registration.at("/responses/409/description").asString()).contains("PLATE_ALREADY_REGISTERED");
        assertThat(registration.at("/requestBody/content/application~1json/schema/$ref").asString())
                .endsWith("/VehicleRequest");
        assertThat(docs.at("/components/schemas/VehicleRequest/required").valueStream().map(JsonNode::asString))
                .contains("brand", "plate", "priceCents").doesNotContain("trimName", "version");
    }

    @Test
    void everyErrorAnswersInTheApiErrorFormat() {
        docs.get("paths").forEach(path -> path.forEach(operation -> operation.get("responses").properties()
                .forEach(response -> {
                    if (response.getKey().charAt(0) >= '4') {
                        assertThat(response.getValue().at("/content/application~1json/schema/$ref").asString())
                                .as(response.getKey()).endsWith("/ApiError");
                    }
                })));
        assertThat(docs.at("/components/schemas/ApiError/properties").propertyNames())
                .containsExactlyInAnyOrder("code", "message", "requestId", "fields");
    }

    @Test
    void everyOperationTakesTheRequestIdAndSaysWhoMayCallIt() {
        docs.get("paths").forEach(path -> path.forEach(operation -> {
            assertThat(operation.get("description").asString()).contains("**Acesso:**");
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

    private static List<String> queryParameters(JsonNode operation) {
        return operation.get("parameters").valueStream()
                .filter(parameter -> parameter.get("in").asString().equals("query"))
                .map(parameter -> parameter.get("name").asString())
                .toList();
    }
}
