package com.revenda.vehicles.domain.ports.in;

public interface ReleaseVehicleUseCase {

    /**
     * Devolve o veículo à venda, reservado ou vendido, só se é desta compra. Já liberado ou de outra compra não muda
     * nada; entregue a esta compra contradiz o comando.
     */
    VehicleCommandOutcome release(ReleaseVehicleCommand command);
}
