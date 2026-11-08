package com.revenda.customers.infrastructure.adapters.out.http;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * O serviço de vendas, que diz até quando guardar os dados de cada cliente: endereço, prazos curtos e a registração
 * do token de serviço do {@code customers} (scope {@code sales.read-retention}).
 */
@ConfigurationProperties("revenda.neighbors")
public record SalesNeighborProperties(
        @DefaultValue("http://localhost:8083") String salesUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout,
        @DefaultValue("customers-service") String clientRegistration) {
}
