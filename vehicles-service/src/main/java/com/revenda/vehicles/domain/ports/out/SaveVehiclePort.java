package com.revenda.vehicles.domain.ports.out;

import com.revenda.vehicles.domain.model.Vehicle;

public interface SaveVehiclePort {

    /** Grava o veículo novo; placa, chassi ou RENAVAM repetido sai como o erro de duplicidade do estoque. */
    void insert(Vehicle vehicle);
}
