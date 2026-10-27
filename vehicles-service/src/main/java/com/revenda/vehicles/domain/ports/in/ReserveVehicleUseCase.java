package com.revenda.vehicles.domain.ports.in;

import com.revenda.common.money.Money;

public interface ReserveVehicleUseCase {

    /** Reserva o veículo para a compra e devolve o preço que ela vai cobrar (RN-09). */
    Money reserve(ReserveVehicleCommand command);
}
