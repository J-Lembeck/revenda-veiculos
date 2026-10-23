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

    public record Oidc(
            @NotBlank String issuerUri,
            @NotBlank String rolesClaim,
            String scopePrefix,
            List<String> webClients,
            List<String> serviceClients) {

        public Oidc {
            scopePrefix = scopePrefix == null ? "" : scopePrefix;
            webClients = webClients == null ? List.of() : List.copyOf(webClients);
            serviceClients = serviceClients == null ? List.of() : List.copyOf(serviceClients);
        }
    }

    public record RateLimit(@DefaultValue("true") boolean enabled) {
    }
}
