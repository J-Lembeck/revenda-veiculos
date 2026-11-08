package com.revenda.sales.domain.ports.in;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Até quando a lei pede guardar os dados de um cliente, pelas compras dele: o {@code customers} pergunta antes de
 * anonimizar. Vazio quando não há venda nem compra aberta a proteger.
 */
public interface GetCustomerRetentionUseCase {

    Optional<Instant> retainUntil(UUID customerId);
}
