package com.revenda.sales.infrastructure.adapters.out.http;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Os vizinhos do caminho síncrono: o estoque, o cadastro e o gateway de pagamento. Cada chamada tem o seu
 * {@code connectTimeout} e {@code readTimeout} curtos e nunca é repetida — a falha vira compensação.
 */
@Validated
@ConfigurationProperties("revenda.neighbors")
public record NeighborsProperties(
        URI vehiclesUrl,
        URI customersUrl,
        URI gatewayUrl,
        @NotBlank String gatewayApiKey,
        @DefaultValue("sales-service") String clientRegistration,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout) {

    @Override
    public String toString() {
        return "NeighborsProperties[vehiclesUrl=" + vehiclesUrl + ", customersUrl=" + customersUrl + ", gatewayUrl="
                + gatewayUrl + ", chave omitida]";
    }
}
