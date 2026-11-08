package com.revenda.common.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("revenda.security")
public record ResourceServerProperties(@Valid @NotNull Oidc oidc, @DefaultValue RateLimit rateLimit) {

    /**
     * {@code jwkSetUri} é opcional: as chaves públicas por outro endereço que não o do emissor, para o contêiner que
     * não alcança o endereço que o navegador usa (no compose, {@code keycloak:8180} em vez de {@code localhost:8180}).
     * O {@code iss} do token continua conferido contra o {@code issuerUri}.
     */
    public record Oidc(
            @NotBlank String issuerUri,
            @NotBlank String rolesClaim,
            String scopePrefix,
            List<String> webClients,
            List<String> serviceClients,
            String jwkSetUri) {

        public Oidc {
            jwkSetUri = jwkSetUri == null || jwkSetUri.isBlank() ? null : jwkSetUri;
            scopePrefix = scopePrefix == null ? "" : scopePrefix;
            webClients = webClients == null ? List.of() : List.copyOf(webClients);
            serviceClients = serviceClients == null ? List.of() : List.copyOf(serviceClients);
        }
    }

    public record RateLimit(@DefaultValue("true") boolean enabled) {
    }
}
