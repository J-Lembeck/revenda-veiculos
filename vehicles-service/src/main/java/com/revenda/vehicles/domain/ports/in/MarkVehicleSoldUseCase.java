package com.revenda.vehicles.domain.ports.in;

public interface MarkVehicleSoldUseCase {

    /** Vende o veículo reservado para esta compra. Já vendido a ela não muda nada; qualquer outro estado contradiz. */
    VehicleCommandOutcome markSold(MarkVehicleSoldCommand command);
}
