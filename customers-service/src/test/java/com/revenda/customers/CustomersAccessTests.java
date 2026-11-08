package com.revenda.customers;

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
class CustomersAccessTests extends CustomersApiTest {

    private static final List<Route> CONTRACT = List.of(
            Route.forRoles(HttpMethod.POST, "/customers", Role.CUSTOMER),
            Route.forRoles(HttpMethod.GET, "/customers/me", Role.CUSTOMER),
            Route.forRoles(HttpMethod.PATCH, "/customers/me", Role.CUSTOMER),
            Route.forRoles(HttpMethod.GET, "/customers/{id}", Role.SELLER),
            Route.forRoles(HttpMethod.POST, "/customers/{id}/cpf-reveal", Role.ADMIN),
            Route.forScope(HttpMethod.GET, "/internal/customers/by-subject/{sub}/eligibility", "customers.read-payer"),
            Route.forScope(HttpMethod.GET, "/internal/customers/{id}/payer", "customers.read-payer"),
            Route.forScope(HttpMethod.GET, "/internal/customers/{id}/transfer-data", "customers.read-payer"),
            Route.forScope(HttpMethod.POST, "/internal/customers/{id}/cpf-check", "customers.read-payer"));

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
