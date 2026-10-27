package com.revenda.customers.domain.ports.in;

import java.util.UUID;

/** O pedido do dado completo do cliente {@code customerId} para a compra {@code purchaseId}, que vai à auditoria. */
public record ReadCustomerDataCommand(UUID customerId, UUID purchaseId) {
}
