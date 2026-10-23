package com.revenda.securityprobe;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.error.web.ApiError;
import com.revenda.common.observability.RequestIdFilter;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.common.test.TestTokens;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;

@SpringBootTest(classes = SecurityProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SecurityProbeTests {

    private static final String CUSTOMER_SUBJECT = "0b4c2f6e-cliente";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationContext context;

    @Test
    void publicRouteAnswersWithoutTokenAndTheActorIsAnonymous() {
        ResponseEntity<ActorContext> response = rest.getForEntity("/vehicles", ActorContext.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().type()).isEqualTo(ActorType.ANONYMOUS);
        assertThat(response.getBody().requestId()).isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
    }

    @Test
    void protectedRouteWithoutTokenAnswersUnauthenticatedWithTheBearerChallenge() {
        ResponseEntity<ApiError> response = call(HttpMethod.GET, "/customers/me", null, ApiError.class);

        assertError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(response.getBody().message()).isEqualTo("Faça login para continuar.");
    }

    @Test
    void tokensThatMustNotPassAnswerUnauthenticated() {
        String[] refused = {
                TestTokens.token().roles("CUSTOMER").issuedAgo(Duration.ofHours(1)).encode(),
                TestTokens.token().roles("CUSTOMER").issuer("https://outro-emissor.example.test/realms/revenda").encode(),
                TestTokens.token().roles("CUSTOMER").signedByUnknownKey().encode(),
                TestTokens.token().roles("CUSTOMER").client("cliente-desconhecido").encode(),
                TestTokens.token().roles("CUSTOMER").claim("azp", null).encode(),
                TestTokens.token().roles("CUSTOMER").claim("typ", "ID").encode(),
                TestTokens.token().roles("CUSTOMER").claim("token_use", "id").encode()};

        for (String token : refused) {
            ResponseEntity<ApiError> response = call(HttpMethod.GET, "/customers/me", token, ApiError.class);

            assertError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
            assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        }
    }

    @Test
    void customerReachesTheOwnRoutesAndTheActorCarriesSubjectAndRole() {
        ResponseEntity<ActorContext> response =
                call(HttpMethod.GET, "/customers/me", TestTokens.customer(CUSTOMER_SUBJECT), ActorContext.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(new ActorContext(ActorType.USER, CUSTOMER_SUBJECT, Role.CUSTOMER,
                        response.getHeaders().getFirst(RequestIdFilter.HEADER)));
    }

    @Test
    void wrongRoleAnswersForbiddenInTheCommonFormat() {
        ResponseEntity<ApiError> response =
                call(HttpMethod.GET, "/vehicles/sold", TestTokens.customer(CUSTOMER_SUBJECT), ApiError.class);

        assertError(response, HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("Seu perfil não tem permissão para esta ação.");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("insufficient_scope");
    }

    @Test
    void adminHasEverythingTheSellerHasButNotTheCustomerRoutes() {
        String admin = TestTokens.admin("admin-1");

        assertThat(call(HttpMethod.GET, "/vehicles/sold", admin, ActorContext.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/purchases", admin, ActorContext.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, "/customers/7/cpf-reveal", admin, ActorContext.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/customers/me", admin, ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sellerDoesNotHaveWhatOnlyTheAdminHas() {
        String seller = TestTokens.seller("vendedora-1");

        assertThat(call(HttpMethod.GET, "/vehicles/sold", seller, ActorContext.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertError(call(HttpMethod.POST, "/customers/7/cpf-reveal", seller, ApiError.class),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void theHighestRoleIsTheOneTheActorCarries() {
        ResponseEntity<ActorContext> response = call(HttpMethod.GET, "/session",
                TestTokens.person("admin-2", Role.CUSTOMER, Role.ADMIN), ActorContext.class);

        assertThat(response.getBody().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void serviceWithTheScopeReachesTheInternalRouteAndIsTheActor() {
        ResponseEntity<ActorContext> response = call(HttpMethod.POST, "/internal/vehicles/7/reservations",
                TestTokens.service(TestTokens.SALES_CLIENT, "vehicles.reserve", "customers.read-payer"),
                ActorContext.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().type()).isEqualTo(ActorType.SERVICE);
        assertThat(response.getBody().id()).isEqualTo(TestTokens.SALES_CLIENT);
        assertThat(response.getBody().role()).isNull();
    }

    @Test
    void serviceWithoutTheScopeIsForbidden() {
        ResponseEntity<ApiError> response = call(HttpMethod.POST, "/internal/vehicles/7/reservations",
                TestTokens.service(TestTokens.SALES_CLIENT, "customers.read-payer"), ApiError.class);

        assertError(response, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void personTokenNeverEntersTheInternalRouteEvenCarryingTheScope() {
        String person = TestTokens.token().roles("ADMIN").scopes("vehicles.reserve").encode();

        ResponseEntity<ApiError> response =
                call(HttpMethod.POST, "/internal/vehicles/7/reservations", person, ApiError.class);

        assertError(response, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void openRouteWithWildcardNeverOpensAnInternalRoute() {
        assertError(call(HttpMethod.GET, "/internal/vehicles/7", null, ApiError.class),
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(call(HttpMethod.GET, "/internal/vehicles/7", TestTokens.customer(CUSTOMER_SUBJECT), ApiError.class),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void serviceTokenNeverPassesARoleRouteEvenCarryingARole() {
        String service = TestTokens.token().client(TestTokens.SALES_CLIENT).roles("ADMIN").encode();

        assertError(call(HttpMethod.GET, "/vehicles/sold", service, ApiError.class), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void accessDeniedInsideTheControllerIsForbiddenAndNotAnInternalError() {
        ResponseEntity<ApiError> response =
                call(HttpMethod.GET, "/denied-inside", TestTokens.customer(CUSTOMER_SUBJECT), ApiError.class);

        assertError(response, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void unknownRouteWithTokenAnswersNotFound() {
        ResponseEntity<ApiError> response =
                call(HttpMethod.GET, "/rota-que-nao-existe", TestTokens.customer(CUSTOMER_SUBJECT), ApiError.class);

        assertError(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void healthIsPublic() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void sensitiveRouteIsLimitedPerAddressBeforeTheTokenIsChecked() {
        for (int attempt = 0; attempt < SecurityProbeApplication.PURCHASE_LIMIT; attempt++) {
            assertThat(call(HttpMethod.POST, "/purchases", null, ApiError.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<ApiError> refused =
                call(HttpMethod.POST, "/purchases", TestTokens.customer(CUSTOMER_SUBJECT), ApiError.class);

        assertError(refused, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        assertThat(Long.parseLong(refused.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);
        assertThat(call(HttpMethod.GET, "/purchases", TestTokens.customer(CUSTOMER_SUBJECT), ActorContext.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void noGeneratedPasswordUserExists() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    private <T> ResponseEntity<T> call(HttpMethod method, String path, String token, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(headers), type);
    }

    private static void assertError(ResponseEntity<ApiError> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().requestId())
                .isNotBlank()
                .isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
    }
}
