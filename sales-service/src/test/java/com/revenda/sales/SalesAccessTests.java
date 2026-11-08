package com.revenda.sales;

import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import com.revenda.common.test.RouteAccessMatrix;
import com.revenda.common.test.RouteAccessMatrix.OwnedRoute;
import com.revenda.common.test.TestTokens;
import java.util.List;
import java.util.UUID;
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
 * {@link SecurityRoutes}, os endpoints e uma chamada de cada tipo de chamador; e a compra de outro cliente, que
 * responde 404.
 */
class SalesAccessTests extends SalesApiTest {

    private static final List<Route> CONTRACT = List.of(
            Route.forRoles(HttpMethod.POST, "/purchases", Role.CUSTOMER),
            Route.forRoles(HttpMethod.GET, "/purchases", Role.CUSTOMER, Role.SELLER),
            Route.forRoles(HttpMethod.GET, "/purchases/{id}", Role.CUSTOMER, Role.SELLER),
            Route.forRoles(HttpMethod.POST, "/purchases/{id}/cancellation", Role.CUSTOMER),
            Route.forRoles(HttpMethod.POST, "/purchases/{id}/pickup", Role.SELLER),
            Route.forRoles(HttpMethod.GET, "/purchases/{id}/document", Role.CUSTOMER, Role.SELLER),
            Route.open(HttpMethod.POST, "/webhooks/payments"),
            Route.forScope(HttpMethod.GET, "/internal/customers/{id}/retention", "sales.read-retention"));

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

    @TestFactory
    Stream<DynamicTest> anotherCustomersPurchaseIsNotFound() {
        String owner = newCustomer();
        UUID purchase = awaitingPurchase(owner);
        String path = PURCHASES + "/" + purchase;
        return matrix().ownership(List.of(
                        new OwnedRoute(HttpMethod.GET, path),
                        new OwnedRoute(HttpMethod.GET, path + "/document"),
                        new OwnedRoute(HttpMethod.POST, path + "/cancellation")),
                customer(owner), customer(newCustomer()));
    }

    private RouteAccessMatrix matrix() {
        return new RouteAccessMatrix(CONTRACT, routes, mappings, api(), TestTokens.CUSTOMERS_CLIENT);
    }
}
