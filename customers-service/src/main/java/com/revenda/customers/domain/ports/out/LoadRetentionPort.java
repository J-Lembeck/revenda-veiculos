package com.revenda.customers.domain.ports.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Até quando a lei pede guardar os dados do cliente, pelo que as compras dele dizem; vazio se nada pede. Sem resposta
 * do {@code sales}, {@code DependencyUnavailableException}.
 */
public interface LoadRetentionPort {

    Optional<Instant> retainUntil(UUID customerId);
}
