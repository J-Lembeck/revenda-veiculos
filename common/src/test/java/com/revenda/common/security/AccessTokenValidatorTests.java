package com.revenda.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AccessTokenValidatorTests {

    private final AccessTokenValidator validator = new AccessTokenValidator(new ResourceServerProperties.Oidc(
            "https://idp.example.test/realms/revenda", "realm_access.roles", "",
            List.of("revenda-web"), List.of("sales-service")));

    @Test
    void acceptsAccessTokenOfAWebClient() {
        assertThat(valid(claims -> {
            claims.put("typ", "Bearer");
            claims.put("azp", "revenda-web");
        })).isTrue();
    }

    @Test
    void acceptsServiceTokenIdentifiedOnlyByClientId() {
        assertThat(valid(claims -> {
            claims.put("token_use", "access");
            claims.put("client_id", "sales-service");
        })).isTrue();
    }

    @Test
    void authorizedPartyWinsOverClientId() {
        assertThat(valid(claims -> {
            claims.put("azp", "cliente-desconhecido");
            claims.put("client_id", "revenda-web");
        })).isFalse();
    }

    @Test
    void refusesUnknownOrMissingClient() {
        assertThat(valid(claims -> claims.put("azp", "cliente-desconhecido"))).isFalse();
        assertThat(valid(claims -> claims.put("typ", "Bearer"))).isFalse();
    }

    @Test
    void refusesIdTokens() {
        assertThat(valid(claims -> {
            claims.put("typ", "ID");
            claims.put("azp", "revenda-web");
        })).isFalse();
        assertThat(valid(claims -> {
            claims.put("token_use", "id");
            claims.put("client_id", "revenda-web");
        })).isFalse();
    }

    @Test
    void refusesOtherKeycloakTokenTypes() {
        assertThat(valid(claims -> {
            claims.put("typ", "Refresh");
            claims.put("azp", "revenda-web");
        })).isFalse();
    }

    private boolean valid(Consumer<Map<String, Object>> claims) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("sujeito-1").claims(claims).build();
        return !validator.validate(jwt).hasErrors();
    }
}
