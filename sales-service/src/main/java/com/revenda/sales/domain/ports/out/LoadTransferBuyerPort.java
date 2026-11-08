package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.TransferBuyer;
import java.util.UUID;

/**
 * O comprador para o documento de transferência desta compra, lido do cadastro na hora (a leitura fica auditada lá).
 * Sem resposta, {@code DependencyUnavailableException}.
 */
public interface LoadTransferBuyerPort {

    TransferBuyer loadBuyer(UUID customerId, UUID purchaseId);
}
