package com.revenda.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/** Toda rota do gateway pede a chave de API: sem ela ou errada, {@code 401}; com ela, passa. */
class PaymentGatewayAccessTests extends PaymentGatewayApiTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void theGatewayHasTheFiveRoutesOfTheContract() {
        assertThat(endpoints().map(Endpoint::toString)).containsExactlyInAnyOrder(
                "POST /charges", "GET /charges", "POST /charges/{}/cancel", "POST /charges/{}/refund",
                "POST /charges/{}/simulate");
    }

    @TestFactory
    Stream<DynamicTest> everyRouteNeedsTheApiKey() {
        return endpoints().flatMap(endpoint -> Stream.of(
                DynamicTest.dynamicTest(endpoint + " sem chave", () -> assertRefused(endpoint, anonymous())),
                DynamicTest.dynamicTest(endpoint + " com outra chave",
                        () -> assertRefused(endpoint, anonymous().withHeader("X-Api-Key", "chave-errada"))),
                DynamicTest.dynamicTest(endpoint + " com a chave", () -> {
                    ResponseEntity<JsonNode> response = call(endpoint, api());
                    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
                })));
    }

    private static void assertRefused(Endpoint endpoint, ApiDriver caller) {
        ResponseEntity<JsonNode> response = call(endpoint, caller);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    private static ResponseEntity<JsonNode> call(Endpoint endpoint, ApiDriver caller) {
        String path = endpoint.pattern().replace("{}", "chg_" + UUID.randomUUID().toString().replace("-", ""));
        Object body = endpoint.method() == HttpMethod.GET ? null : Map.of();
        return caller.exchange(endpoint.method(), path, body, JsonNode.class);
    }

    private Stream<Endpoint> endpoints() {
        return mappings.getHandlerMethods().keySet().stream()
                .flatMap(info -> methods(info).flatMap(method -> info.getPatternValues().stream()
                        .filter(pattern -> !pattern.startsWith("/error"))
                        .map(pattern -> new Endpoint(method, pattern.replaceAll("\\{[^}]+}", "{}")))));
    }

    private static Stream<HttpMethod> methods(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.stream().map(method -> HttpMethod.valueOf(method.name()));
    }

    private record Endpoint(HttpMethod method, String pattern) {
        @Override
        public String toString() {
            return method.name() + " " + pattern;
        }
    }
}
