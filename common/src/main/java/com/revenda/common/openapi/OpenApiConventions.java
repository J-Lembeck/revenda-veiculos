package com.revenda.common.openapi;

import com.revenda.common.error.web.ApiError;
import com.revenda.common.security.RateLimitRule;
import com.revenda.common.security.Role;
import com.revenda.common.security.Route;
import com.revenda.common.security.SecurityRoutes;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * O que vale para toda rota do serviço, tirado das regras de acesso dele ({@link SecurityRoutes}) na mesma ordem
 * da cadeia de segurança: quem pode chamar, o login, o {@code X-Request-Id} e as respostas de erro comuns. Todo
 * erro sai no formato {@link ApiError}, inclusive os que o controller declara — ele diz só os códigos da rota.
 */
public class OpenApiConventions implements OpenApiCustomizer {

    static final String OIDC = "oidc";
    static final String BEARER = "bearer";
    static final String API_ERROR = "ApiError";

    private static final String JSON = "application/json";
    private static final PathPatternParser PATTERNS = PathPatternParser.defaultInstance;

    private final List<Route> routes;
    private final List<RateLimitRule> rateLimits;
    private final OpenApiProperties properties;

    public OpenApiConventions(List<SecurityRoutes> declared, OpenApiProperties properties) {
        List<Route> all = declared.stream().flatMap(routes -> routes.routes().stream()).toList();
        this.routes = Stream.concat(all.stream().filter(route -> route.scope() != null),
                all.stream().filter(route -> route.scope() == null)).toList();
        this.rateLimits = declared.stream().flatMap(routes -> routes.rateLimits().stream()).toList();
        this.properties = properties;
    }

    @Override
    public void customise(OpenAPI openApi) {
        openApi.info(new Info().title(properties.title()).version(properties.version())
                .description(properties.description()));
        Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
        openApi.components(components);
        ModelConverters.getInstance().readAll(ApiError.class).forEach(components::addSchemas);
        components.getSchemas().get(API_ERROR).description("Todo erro: `code` estável (o front decide por ele), "
                + "`message` em português, o `requestId` do log e, na validação, cada campo em `fields`.");
        components.addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                .bearerFormat("JWT").description("Token de acesso do provedor de identidade, colado à mão."));
        if (properties.authorizationUrl() != null && properties.tokenUrl() != null) {
            components.addSecuritySchemes(OIDC, new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
                    .description("Login de pessoa por authorization code com PKCE.")
                    .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                            .authorizationUrl(properties.authorizationUrl())
                            .tokenUrl(properties.tokenUrl())
                            .scopes(new Scopes().addString("openid", "identidade do usuário")))));
        }
        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach(
                    (method, operation) -> document(path, HttpMethod.valueOf(method.name()), operation)));
        }
    }

    private void document(String path, HttpMethod method, Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        Optional<Route> route = routes.stream().filter(candidate -> matches(candidate, method, path)).findFirst();
        if (route.isPresent() && route.get().scope() != null) {
            access(operation, "Interna: só token de serviço com o scope `" + route.get().scope()
                    + "`. Token de pessoa, mesmo da equipe, recebe `403`.", bearerOnly());
            error(operation, "403", "`FORBIDDEN` — token de pessoa ou serviço sem o scope.");
        } else if (route.isPresent() && route.get().roles().isEmpty()) {
            access(operation, "Aberta: não precisa de token. Token enviado é conferido, e o inválido recebe `401`.",
                    Stream.concat(personOrBearer().stream(), Stream.of(new SecurityRequirement())).toList());
        } else if (route.isPresent()) {
            access(operation, "Papéis: " + roles(route.get()) + ".", personOrBearer());
            error(operation, "403", "`FORBIDDEN` — papel sem permissão.");
        } else {
            access(operation, "Qualquer pessoa com token de acesso válido.", personOrBearer());
        }
        error(operation, "401", "`UNAUTHENTICATED` — sem token, token inválido ou vencido, ID token ou cliente não "
                + "aceito.");
        if (rateLimits.stream().anyMatch(rule -> rule.method() == method && matches(rule.pattern(), path))) {
            error(operation, "429", "`RATE_LIMITED` — limite de chamadas desta rede; `Retry-After` diz quando voltar.");
        }
        error(operation, "500", "`INTERNAL_ERROR` — erro inesperado; o corpo traz só o `requestId`.");
        operation.getResponses().forEach((status, response) -> {
            if (status.charAt(0) >= '4') {
                response.setContent(apiError());
            }
        });
        boolean hasRequestId = operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(parameter -> "X-Request-Id".equalsIgnoreCase(parameter.getName()));
        if (!hasRequestId) {
            operation.addParametersItem(new HeaderParameter().name("X-Request-Id").required(false)
                    .description("Id da requisição (`[A-Za-z0-9._-]{8,64}`); gerado se faltar ou vier fora do "
                            + "formato, e devolvido na resposta.")
                    .schema(new StringSchema()));
        }
    }

    private List<SecurityRequirement> personOrBearer() {
        return properties.authorizationUrl() == null ? bearerOnly()
                : List.of(new SecurityRequirement().addList(OIDC), new SecurityRequirement().addList(BEARER));
    }

    private static List<SecurityRequirement> bearerOnly() {
        return List.of(new SecurityRequirement().addList(BEARER));
    }

    private static void access(Operation operation, String rule, List<SecurityRequirement> security) {
        operation.setSecurity(security);
        String note = "**Acesso:** " + rule;
        operation.setDescription(operation.getDescription() == null || operation.getDescription().isBlank() ? note
                : operation.getDescription() + "\n\n" + note);
    }

    private static void error(Operation operation, String status, String description) {
        operation.getResponses().putIfAbsent(status, new ApiResponse().description(description));
    }

    private static Content apiError() {
        return new Content().addMediaType(JSON,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR)));
    }

    private static String roles(Route route) {
        String listed = route.roles().stream().map(Role::name).sorted().collect(Collectors.joining(", "));
        return route.roles().contains(Role.SELLER) && !route.roles().contains(Role.ADMIN)
                ? listed + " (o ADMIN herda o SELLER)" : listed;
    }

    private static boolean matches(Route route, HttpMethod method, String path) {
        return (route.method() == null || route.method() == method) && matches(route.pattern(), path);
    }

    private static boolean matches(String pattern, String path) {
        return PATTERNS.parse(pattern).matches(PathContainer.parsePath(path));
    }
}
