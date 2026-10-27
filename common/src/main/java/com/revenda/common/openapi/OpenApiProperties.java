package com.revenda.common.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * O cabeçalho do contrato e o login do Swagger UI. Sem {@code authorizationUrl} e {@code tokenUrl}, o contrato só
 * oferece colar um token de acesso.
 */
@ConfigurationProperties("revenda.openapi")
public record OpenApiProperties(
        @DefaultValue("API") String title,
        String description,
        @DefaultValue("v1") String version,
        String authorizationUrl,
        String tokenUrl) {
}
