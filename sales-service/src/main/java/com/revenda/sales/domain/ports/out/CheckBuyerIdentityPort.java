package com.revenda.sales.domain.ports.out;

import java.util.UUID;

/**
 * Se o CPF do documento apresentado na retirada é o do cadastro do comprador, conferido no cadastro pelo blind index
 * sem revelar o CPF de lá (a conferência fica auditada lá, com a compra). CPF que não confere sai como erro do campo
 * {@code cpf}; sem resposta, {@code DependencyUnavailableException}.
 */
public interface CheckBuyerIdentityPort {

    boolean matches(UUID customerId, UUID purchaseId, String cpf);
}
