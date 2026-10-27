package com.revenda.vehicles.domain.ports.out;

import com.revenda.vehicles.domain.model.Vehicle;

public interface SaveVehiclePort {

    /** Grava o veículo novo; placa, chassi ou RENAVAM repetido sai como o erro de duplicidade do estoque. */
    void insert(Vehicle vehicle);

    /**
     * Grava os dados do veículo editado só se o estoque ainda está na versão anterior a ele e o veículo segue à venda
     * ou reservado; devolve se gravou. Duplicidade sai como no {@link #insert}.
     */
    boolean updateDetails(Vehicle edited);
}
