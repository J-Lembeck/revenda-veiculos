package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.Payer;
import java.util.UUID;

/**
 * Os dados do pagador, lidos do cadastro só para emitir a cobrança desta compra (a leitura fica auditada lá). Sem
 * resposta, {@code DependencyUnavailableException}.
 */
public interface LoadPayerPort {

    Payer load(UUID customerId, UUID purchaseId);
}
