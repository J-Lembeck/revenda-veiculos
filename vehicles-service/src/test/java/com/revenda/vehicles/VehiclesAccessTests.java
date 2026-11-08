package com.revenda.vehicles;

import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import com.revenda.common.test.RouteAccessMatrix;
import com.revenda.common.test.TestTokens;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * O teste de acesso do serviço: a tabela de rotas da API documentada, escrita aqui à mão, conferida contra o
 * {@link SecurityRoutes}, os endpoints e uma chamada de cada tipo de chamador.
 */
class VehiclesAccessTests extends VehiclesApiTest {

    private static final List<Route> CONTRACT = List.of(
            Route.forRoles(HttpMethod.POST, "/vehicles", Role.SELLER),
            Route.forRoles(HttpMethod.PUT, "/vehicles/{id}", Role.SELLER),
            Route.forRoles(HttpMethod.GET, "/vehicles/sold", Role.SELLER),
            Route.open(HttpMethod.GET, "/vehicles"),
            Route.open(HttpMethod.GET, "/vehicles/{id}"),
            Route.forScope(HttpMethod.POST, "/internal/vehicles/{id}/reservations", "vehicles.reserve"),
            Route.forScope(HttpMethod.GET, "/internal/vehicles/{id}", "vehicles.read"));

    @Autowired
    private List<SecurityRoutes> routes;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void theAccessRulesAreTheDocumentedOnes() {
        matrix().assertTheRulesAreTheContract();
    }

    @Test
    void everyEndpointIsInTheContractAndEveryRouteHasAnEndpoint() {
        matrix().assertEveryEndpointIsInTheContract();
    }

    @TestFactory
    Stream<DynamicTest> eachRouteWithEachCaller() {
        return matrix().eachRouteWithEachCaller();
    }

    private RouteAccessMatrix matrix() {
        return new RouteAccessMatrix(CONTRACT, routes, mappings, api(), TestTokens.SALES_CLIENT);
    }
}
