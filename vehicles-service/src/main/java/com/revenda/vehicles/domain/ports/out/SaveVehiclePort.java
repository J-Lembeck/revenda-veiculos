package com.revenda.vehicles.domain.ports.out;

import com.revenda.common.money.Money;
import com.revenda.vehicles.domain.model.Vehicle;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SaveVehiclePort {

    /** Grava o veículo novo; placa, chassi ou RENAVAM repetido sai como o erro de duplicidade do estoque. */
    void insert(Vehicle vehicle);

    /**
     * Grava os dados do veículo editado só se o estoque ainda está na versão anterior a ele e o veículo segue à venda
     * ou reservado; devolve se gravou. Duplicidade sai como no {@link #insert}.
     */
    boolean updateDetails(Vehicle edited);

    /**
     * Reserva o veículo para a compra só se ele está à venda, num {@code UPDATE} condicional; devolve o preço do
     * momento, ou vazio se não havia veículo à venda com esse id.
     */
    Optional<Money> reserveIfAvailable(UUID id, UUID purchaseId, Instant now);
}
