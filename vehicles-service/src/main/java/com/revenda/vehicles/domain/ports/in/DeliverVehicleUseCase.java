package com.revenda.vehicles.domain.ports.in;

public interface DeliverVehicleUseCase {

    /**
     * Dá baixa no veículo vendido para esta compra. Já entregue a ela não muda nada; ainda reservado por ela ainda não
     * se aplica; qualquer outro estado contradiz.
     */
    VehicleCommandOutcome deliver(DeliverVehicleCommand command);
}
