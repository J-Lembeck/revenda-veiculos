package com.revenda.customers.infrastructure.adapters.out.http;

import com.revenda.common.error.DependencyUnavailableException;
import com.revenda.common.http.ServiceCalls;
import com.revenda.customers.domain.ports.out.LoadRetentionPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** O serviço de vendas, com o token de serviço de scope {@code sales.read-retention}; a resposta não tem dado pessoal. */
@Component
class SalesHttpClient implements LoadRetentionPort {

    private static final String NEIGHBOR = "serviço de vendas";

    private final RestClient rest;

    SalesHttpClient(@Qualifier("salesRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Optional<Instant> retainUntil(UUID customerId) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            RetentionResponse answer = rest.get()
                    .uri("/internal/customers/{id}/retention", customerId)
                    .retrieve()
                    .body(RetentionResponse.class);
            if (answer == null) {
                throw new DependencyUnavailableException(NEIGHBOR, "resposta vazia");
            }
            return Optional.ofNullable(answer.retainUntil());
        } catch (DependencyUnavailableException unreadable) {
            throw unreadable;
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(failure));
        }
    }

    record RetentionResponse(Instant retainUntil) {
    }
}
