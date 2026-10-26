package com.revenda.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.revenda.common.error.web.ApiError;
import com.revenda.common.observability.RequestIdFilter;
import com.revenda.common.security.ResourceServerProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;

@Import(TestTokenConfiguration.class)
@TestPropertySource(properties = "revenda.security.oidc.issuer-uri=" + TestTokens.ISSUER)
public abstract class ResourceServerContract extends ServiceFoundationContract {

    private static final String UNKNOWN_ROUTE = "/rota-que-nao-existe";
    private static final String UNDECLARED_INTERNAL_ROUTE = "/internal/rota-nao-declarada";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ResourceServerProperties properties;

    @Override
    protected ApiDriver api() {
        return anonymous().asCustomer("contrato-cliente");
    }

    @Test
    void callWithoutTokenAnswersUnauthenticatedInTheCommonFormat() {
        ResponseEntity<ApiError> response = get(anonymous(), UNKNOWN_ROUTE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
        assertThat(response.getBody().requestId())
                .isNotBlank()
                .isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
    }

    @Test
    void invalidTokenAnswersUnauthenticated() {
        ResponseEntity<ApiError> response = get(anonymous().as("nao-e-um-token"), UNKNOWN_ROUTE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void actuatorEndpointsOtherThanHealthNeedAToken() {
        ResponseEntity<String> response = anonymous().exchange(HttpMethod.GET, "/actuator/env", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void personTokenNeverEntersAnInternalRoute() {
        for (ApiDriver person : List.of(anonymous().asCustomer("contrato-cliente"), anonymous().asAdmin("contrato-admin"))) {
            ResponseEntity<ApiError> response = get(person, UNDECLARED_INTERNAL_ROUTE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
            assertThat(response.getBody().requestId()).isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
        }
    }

    @Test
    void undeclaredInternalRouteIsDeniedToServicesToo() {
        List<String> serviceClients = properties.oidc().serviceClients();
        assumeFalse(serviceClients.isEmpty(), "nenhum serviço chama este");

        ResponseEntity<ApiError> response = get(anonymous().asService(serviceClients.getFirst(), "vehicles.reserve",
                "customers.read-payer", "sales.read-retention"), UNDECLARED_INTERNAL_ROUTE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void noGeneratedPasswordUserExists() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    private static ResponseEntity<ApiError> get(ApiDriver caller, String path) {
        return caller.exchange(HttpMethod.GET, path, null, ApiError.class);
    }
}
