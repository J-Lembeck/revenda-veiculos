package com.revenda.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class AccessTokenConverterTests {

    private static final ResourceServerProperties.Oidc KEYCLOAK = new ResourceServerProperties.Oidc(
            "https://idp.example.test/realms/revenda", "realm_access.roles", "",
            List.of("revenda-web"), List.of("sales-service"));
    private static final ResourceServerProperties.Oidc COGNITO = new ResourceServerProperties.Oidc(
            "https://cognito-idp.sa-east-1.amazonaws.com/sa-east-1_exemplo", "cognito:groups", "revenda/",
            List.of("web-client-id"), List.of("sales-client-id"));

    @Test
    void personFromKeycloakKeepsOnlyTheKnownRoles() {
        ActorAuthentication actor = convert(KEYCLOAK, claims -> {
            claims.put("azp", "revenda-web");
            claims.put("realm_access", Map.of("roles",
                    List.of("CUSTOMER", "offline_access", "uma_authorization", "default-roles-revenda")));
        });

        assertThat(authorities(actor)).containsExactly("ROLE_CUSTOMER");
        assertThat(actor.getActorType()).isEqualTo(ActorType.USER);
        assertThat(actor.getName()).isEqualTo("sujeito-1");
        assertThat(actor.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void personFromCognitoReadsTheGroupsClaim() {
        ActorAuthentication actor = convert(COGNITO, claims -> {
            claims.put("client_id", "web-client-id");
            claims.put("cognito:groups", List.of("SELLER"));
        });

        assertThat(authorities(actor)).containsExactly("ROLE_SELLER");
        assertThat(actor.getRole()).isEqualTo(Role.SELLER);
    }

    @Test
    void theHighestRoleIsTheActorRole() {
        ActorAuthentication actor = convert(KEYCLOAK, claims -> {
            claims.put("azp", "revenda-web");
            claims.put("realm_access", Map.of("roles", List.of("CUSTOMER", "ADMIN", "SELLER")));
        });

        assertThat(authorities(actor)).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_SELLER", "ROLE_ADMIN");
        assertThat(actor.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void personWithoutRoleHasNoAuthorityAndNoRole() {
        ActorAuthentication actor = convert(COGNITO, claims -> claims.put("client_id", "web-client-id"));

        assertThat(actor.getAuthorities()).isEmpty();
        assertThat(actor.getActorType()).isEqualTo(ActorType.USER);
        assertThat(actor.getRole()).isNull();
    }

    @Test
    void personNeverGetsScopes() {
        ActorAuthentication actor = convert(KEYCLOAK, claims -> {
            claims.put("azp", "revenda-web");
            claims.put("scope", "openid email vehicles.reserve");
            claims.put("realm_access", Map.of("roles", List.of("CUSTOMER")));
        });

        assertThat(authorities(actor)).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void serviceGetsScopesAndNeverRoles() {
        ActorAuthentication actor = convert(KEYCLOAK, claims -> {
            claims.put("azp", "sales-service");
            claims.put("client_id", "sales-service");
            claims.put("scope", "vehicles.reserve customers.read-payer");
            claims.put("realm_access", Map.of("roles", List.of("ADMIN")));
        });

        assertThat(authorities(actor)).containsExactlyInAnyOrder("SCOPE_vehicles.reserve", "SCOPE_customers.read-payer");
        assertThat(actor.getActorType()).isEqualTo(ActorType.SERVICE);
        assertThat(actor.getName()).isEqualTo("sales-service");
        assertThat(actor.getRole()).isNull();
    }

    @Test
    void tokenWithoutClientIsNeverAService() {
        ActorAuthentication actor = convert(KEYCLOAK, claims -> claims.put("scope", "vehicles.reserve"));

        assertThat(actor.getActorType()).isEqualTo(ActorType.USER);
        assertThat(actor.getAuthorities()).isEmpty();
    }

    @Test
    void scopePrefixOfTheResourceServerIsRemovedAndOtherScopesIgnored() {
        ActorAuthentication actor = convert(COGNITO, claims -> {
            claims.put("client_id", "sales-client-id");
            claims.put("scope", "revenda/vehicles.reserve aws.cognito.signin.user.admin revenda/");
        });

        assertThat(authorities(actor)).containsExactly("SCOPE_vehicles.reserve");
    }

    private static ActorAuthentication convert(ResourceServerProperties.Oidc oidc,
            Consumer<Map<String, Object>> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("sujeito-1")
                .claims(claims)
                .build();
        return (ActorAuthentication) new AccessTokenConverter(oidc).convert(jwt);
    }

    private static List<String> authorities(ActorAuthentication actor) {
        return actor.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
