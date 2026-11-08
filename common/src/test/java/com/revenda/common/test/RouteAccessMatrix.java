package com.revenda.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/**
 * O teste de acesso de um serviço contra o contrato: a tabela de rotas que o teste escreve à mão, copiada da API
 * documentada, e não lida do código. Confere que o {@link SecurityRoutes} do serviço é igual a ela, que todo endpoint
 * está nela e toda rota dela tem endpoint, e chama cada rota com cada tipo de chamador esperando o que ela manda —
 * {@code 401}, {@code 403} ou passar pela segurança. Passar é não receber {@code UNAUTHENTICATED} nem
 * {@code FORBIDDEN}; o que vem depois (validação, {@code 404}, regra de negócio) é dos testes de cada rota.
 */
public final class RouteAccessMatrix {

    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+}");
    private static final List<String> FRAMEWORK_PATHS = List.of("/error", "/v3/api-docs", "/swagger-ui");
    private static final String UNKNOWN_CLIENT = "cliente-desconhecido";
    private static final String OTHER_SCOPE = "escopo.de.outra.rota";

    /** Quem chama: ninguém, cada papel de pessoa e três tokens de serviço. */
    public enum Caller {
        ANONYMOUS, CUSTOMER, SELLER, ADMIN, SERVICE_WITH_SCOPE, SERVICE_WITHOUT_SCOPE, UNKNOWN_SERVICE
    }

    /** Uma rota com dono, já com o id de um recurso do dono no caminho. */
    public record OwnedRoute(HttpMethod method, String path) {
    }

    private final List<Route> routes;
    private final List<Route> declared;
    private final RequestMappingHandlerMapping mappings;
    private final ApiDriver api;
    private final String serviceClient;

    /**
     * @param contract as rotas da API documentada, escritas no teste
     * @param serviceClient o cliente de serviço que o serviço aceita ({@code OIDC_SERVICE_CLIENTS})
     */
    public RouteAccessMatrix(List<Route> contract, Collection<SecurityRoutes> declared,
            RequestMappingHandlerMapping mappings, ApiDriver api, String serviceClient) {
        this.routes = List.copyOf(contract);
        this.declared = declared.stream().flatMap(routes -> routes.routes().stream()).toList();
        this.mappings = mappings;
        this.api = api.anonymous();
        this.serviceClient = serviceClient;
    }

    /** O {@code SecurityRoutes} do serviço é o contrato, rota por rota, com os mesmos papéis e scope. */
    public void assertTheRulesAreTheContract() {
        assertThat(declared.stream().map(RouteAccessMatrix::rule).toList())
                .as("regras do SecurityRoutes × contrato")
                .containsExactlyInAnyOrderElementsOf(routes.stream().map(RouteAccessMatrix::rule).toList());
    }

    /** Todo endpoint do serviço está no contrato, e toda rota do contrato tem endpoint. */
    public void assertEveryEndpointIsInTheContract() {
        Set<String> endpoints = endpoints();
        Set<String> contract = routes.stream()
                .map(route -> key(route.method() == null ? "*" : route.method().name(), route.pattern()))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(endpoints).as("endpoints fora do contrato").isSubsetOf(contract);
        assertThat(contract).as("rotas do contrato sem endpoint").isSubsetOf(endpoints);
    }

    /** Uma chamada por rota e por chamador, com o resultado que a regra da rota manda. */
    public Stream<DynamicTest> eachRouteWithEachCaller() {
        return routes.stream().flatMap(route -> Stream.of(Caller.values()).map(caller -> DynamicTest.dynamicTest(
                route.method() + " " + route.pattern() + " por " + caller, () -> assertCall(route, caller))));
    }

    /**
     * A rota de dono chamada por outro cliente responde {@code 404}, nunca {@code 403}; pelo dono, passa. Toda rota de
     * {@code CUSTOMER} com id no caminho precisa estar na lista.
     */
    public Stream<DynamicTest> ownership(List<OwnedRoute> owned, ApiDriver owner, ApiDriver stranger) {
        Set<String> covered = owned.stream()
                .map(route -> key(route.method().name(), patternOf(route)))
                .collect(Collectors.toSet());
        List<String> customerRoutesWithId = routes.stream()
                .filter(route -> route.roles().contains(Role.CUSTOMER) && VARIABLE.matcher(route.pattern()).find())
                .map(route -> key(route.method().name(), route.pattern()))
                .toList();
        assertThat(covered).as("rotas de dono sem o teste de outro cliente").containsAll(customerRoutesWithId);

        return owned.stream().flatMap(route -> Stream.of(
                DynamicTest.dynamicTest(route.method() + " " + route.path() + " por outro cliente → 404", () -> {
                    ResponseEntity<JsonNode> response = stranger.exchange(route.method(), route.path(), body(route.method()),
                            JsonNode.class);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
                }),
                DynamicTest.dynamicTest(route.method() + " " + route.path() + " pelo dono", () -> {
                    ResponseEntity<JsonNode> response = owner.exchange(route.method(), route.path(), body(route.method()),
                            JsonNode.class);
                    assertThat(response.getStatusCode()).isNotIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN,
                            HttpStatus.UNAUTHORIZED);
                })));
    }

    private void assertCall(Route route, Caller caller) {
        HttpMethod method = route.method() == null ? HttpMethod.GET : route.method();
        String path = VARIABLE.matcher(route.pattern()).replaceAll(match -> UUID.randomUUID().toString());
        ResponseEntity<JsonNode> response = as(caller, route).exchange(method, path, body(method), JsonNode.class);
        String code = response.getBody() == null || response.getBody().get("code") == null
                ? null
                : response.getBody().get("code").asString();

        if (allowed(route, caller)) {
            assertThat(code).as("código da resposta (%s)", response.getStatusCode())
                    .isNotIn("UNAUTHENTICATED", "FORBIDDEN");
        } else if (caller == Caller.ANONYMOUS || caller == Caller.UNKNOWN_SERVICE) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(code).isEqualTo("UNAUTHENTICATED");
        } else {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(code).isEqualTo("FORBIDDEN");
        }
    }

    private static boolean allowed(Route route, Caller caller) {
        if (caller == Caller.UNKNOWN_SERVICE) {
            return false;
        }
        if (route.scope() != null) {
            return caller == Caller.SERVICE_WITH_SCOPE;
        }
        if (route.roles().isEmpty()) {
            return true;
        }
        return switch (caller) {
            case CUSTOMER -> route.roles().contains(Role.CUSTOMER);
            case SELLER -> route.roles().contains(Role.SELLER);
            case ADMIN -> route.roles().contains(Role.ADMIN) || route.roles().contains(Role.SELLER);
            default -> false;
        };
    }

    private ApiDriver as(Caller caller, Route route) {
        return switch (caller) {
            case ANONYMOUS -> api;
            case CUSTOMER -> api.asCustomer("acesso-cliente");
            case SELLER -> api.asSeller("acesso-vendedora");
            case ADMIN -> api.asAdmin("acesso-admin");
            case SERVICE_WITH_SCOPE -> api.asService(serviceClient, scopesFor(route));
            case SERVICE_WITHOUT_SCOPE -> api.asService(serviceClient, OTHER_SCOPE);
            case UNKNOWN_SERVICE -> api.asService(UNKNOWN_CLIENT, scopesFor(route));
        };
    }

    private String[] scopesFor(Route route) {
        if (route.scope() != null) {
            return new String[] {route.scope()};
        }
        return routes.stream().map(Route::scope).filter(scope -> scope != null).distinct().toArray(String[]::new);
    }

    private static Object body(HttpMethod method) {
        return HttpMethod.GET.equals(method) || HttpMethod.DELETE.equals(method) ? null : Map.of();
    }

    private Set<String> endpoints() {
        Set<String> endpoints = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (FRAMEWORK_PATHS.stream().anyMatch(pattern::startsWith)) {
                    continue;
                }
                if (methods.isEmpty()) {
                    endpoints.add(key("*", pattern));
                }
                methods.forEach(method -> endpoints.add(key(method.name(), pattern)));
            }
        }
        return endpoints;
    }

    private String patternOf(OwnedRoute owned) {
        List<String> candidates = new ArrayList<>();
        for (Route route : routes) {
            if (route.method() != null && route.method().equals(owned.method())
                    && owned.path().matches(VARIABLE.matcher(route.pattern()).replaceAll("[^/]+"))) {
                candidates.add(route.pattern());
            }
        }
        return candidates.isEmpty() ? owned.path() : candidates.getFirst();
    }

    private static String rule(Route route) {
        return key(route.method() == null ? "*" : route.method().name(), route.pattern()) + " "
                + new TreeSet<>(route.roles()) + " " + route.scope();
    }

    private static String key(String method, String pattern) {
        return method + " " + VARIABLE.matcher(pattern).replaceAll("{}");
    }
}
