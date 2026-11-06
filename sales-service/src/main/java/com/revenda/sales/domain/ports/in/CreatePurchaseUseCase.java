package com.revenda.sales.domain.ports.in;

import com.revenda.sales.domain.model.PurchaseDetails;

/**
 * UC-08 (DP-02): reserva o veículo e emite a cobrança numa chamada só. Devolve a compra aguardando o pagamento;
 * veículo indisponível sai como {@code VEHICLE_UNAVAILABLE}, vizinho fora do ar como {@code DEPENDENCY_UNAVAILABLE},
 * com a compra já encerrada e a compensação no outbox.
 */
public interface CreatePurchaseUseCase {

    PurchaseDetails create(CreatePurchaseCommand command);
}
