package com.revenda.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.security.RateLimitRule;
import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class OpenApiConventionsTests {

    private static final SecurityRoutes ROUTES = new SecurityRoutes() {
        @Override
        public List<Route> routes() {
            return List.of(
                    Route.forRoles(HttpMethod.GET, "/things/mine", Role.SELLER),
                    Route.open(HttpMethod.GET, "/things/{id}"),
                    Route.forRoles(HttpMethod.POST, "/things", Role.CUSTOMER, Role.ADMIN),
                    Route.forScope(HttpMethod.POST, "/internal/things/{id}", "things.write"));
        }

        @Override
        public List<RateLimitRule> rateLimits() {
            return List.of(new RateLimitRule(HttpMethod.POST, "/things", 5, Duration.ofMinutes(1)));
        }
    };

    private static final OpenApiProperties PROPERTIES = new OpenApiProperties("Coisas", "Descrição", "v1",
            "http://idp.test/auth", "http://idp.test/token");

    @Test
    void theFirstDeclaredRouteThatMatchesDecidesLikeTheSecurityChain() {
        OpenAPI api = document(PROPERTIES, "/things/mine", "/things/{id}");

        assertThat(get(api, "/things/mine").getDescription())
                .endsWith("**Acesso:** Papéis: SELLER (o ADMIN herda o SELLER).");
        assertThat(get(api, "/things/mine").getResponses()).containsKeys("401", "403", "500");
        assertThat(get(api, "/things/{id}").getSecurity()).containsExactly(
                new SecurityRequirement().addList(OpenApiConventions.OIDC),
                new SecurityRequirement().addList(OpenApiConventions.BEARER), new SecurityRequirement());
        assertThat(get(api, "/things/{id}").getResponses()).containsKeys("401", "500").doesNotContainKey("403");
    }

    @Test
    void roleRoutesOfferTheLoginAndAPastedToken() {
        OpenAPI api = document(PROPERTIES, "/things/mine");

        assertThat(get(api, "/things/mine").getSecurity()).containsExactly(
                new SecurityRequirement().addList(OpenApiConventions.OIDC),
                new SecurityRequirement().addList(OpenApiConventions.BEARER));
        assertThat(api.getComponents().getSecuritySchemes().get(OpenApiConventions.OIDC).getFlows()
                .getAuthorizationCode().getTokenUrl()).isEqualTo("http://idp.test/token");
    }

    @Test
    void withoutTheLoginAddressesOnlyAPastedTokenIsOffered() {
        OpenAPI api = document(new OpenApiProperties("Coisas", null, "v1", null, null), "/things/mine");

        assertThat(get(api, "/things/mine").getSecurity())
                .containsExactly(new SecurityRequirement().addList(OpenApiConventions.BEARER));
        assertThat(api.getComponents().getSecuritySchemes()).containsOnlyKeys(OpenApiConventions.BEARER);
    }

    @Test
    void internalRoutesNameTheScopeAndTakeOnlyAToken() {
        OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/internal/things/{id}",
                new PathItem().post(new Operation().description("Grava a coisa."))));

        new OpenApiConventions(List.of(ROUTES), PROPERTIES).customise(api);

        Operation internal = api.getPaths().get("/internal/things/{id}").getPost();
        assertThat(internal.getDescription()).startsWith("Grava a coisa.\n\n**Acesso:** Interna")
                .contains("`things.write`");
        assertThat(internal.getSecurity()).containsExactly(new SecurityRequirement().addList(OpenApiConventions.BEARER));
    }

    @Test
    void rateLimitedRoutesDocumentTheLimitAndUndeclaredOnesOnlyNeedAToken() {
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/things", new PathItem().post(new Operation()))
                .addPathItem("/other", new PathItem().get(new Operation())));

        new OpenApiConventions(List.of(ROUTES), PROPERTIES).customise(api);

        assertThat(api.getPaths().get("/things").getPost().getResponses()).containsKey("429");
        assertThat(api.getPaths().get("/things").getPost().getDescription()).contains("Papéis: ADMIN, CUSTOMER.");
        assertThat(api.getPaths().get("/other").getGet().getDescription())
                .contains("Qualquer pessoa com token de acesso válido.");
        assertThat(api.getPaths().get("/other").getGet().getResponses()).doesNotContainKeys("403", "429");
    }

    @Test
    void everyErrorAnswersInTheApiErrorAndKeepsTheDeclaredDescription() {
        ApiResponse declared = new ApiResponse().description("O do controller.");
        ApiResponse withBody = new ApiResponse().description("Com o tipo de retorno do método.")
                .content(new Content().addMediaType("*/*", new MediaType().schema(new StringSchema())));
        OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/things/{id}", new PathItem().get(new Operation()
                .responses(new ApiResponses().addApiResponse("401", declared).addApiResponse("404", withBody)))));

        new OpenApiConventions(List.of(ROUTES), PROPERTIES).customise(api);

        ApiResponses responses = get(api, "/things/{id}").getResponses();
        assertThat(responses.get("401").getDescription()).isEqualTo("O do controller.");
        assertThat(responses.get("401").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/ApiError");
        assertThat(responses.get("404").getContent()).containsOnlyKeys("application/json");
        assertThat(responses.get("404").getDescription()).isEqualTo("Com o tipo de retorno do método.");
        assertThat(api.getComponents().getSchemas()).containsKeys("ApiError", "FieldViolation");
    }

    @Test
    void everyOperationTakesTheRequestIdOnce() {
        OpenAPI api = document(PROPERTIES, "/things/{id}");

        new OpenApiConventions(List.of(ROUTES), PROPERTIES).customise(api);

        assertThat(get(api, "/things/{id}").getParameters())
                .filteredOn(parameter -> parameter.getName().equals("X-Request-Id")).hasSize(1);
    }

    private static OpenAPI document(OpenApiProperties properties, String... getPaths) {
        Paths paths = new Paths();
        for (String path : getPaths) {
            paths.addPathItem(path, new PathItem().get(new Operation()));
        }
        OpenAPI api = new OpenAPI().paths(paths);
        new OpenApiConventions(List.of(ROUTES), properties).customise(api);
        return api;
    }

    private static Operation get(OpenAPI api, String path) {
        return api.getPaths().get(path).getGet();
    }
}
