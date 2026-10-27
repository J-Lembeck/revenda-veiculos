package com.revenda.vehicles.domain.ports.in;

import com.revenda.vehicles.domain.model.Vehicle;

public interface RegisterVehicleUseCase {

    Vehicle register(RegisterVehicleCommand command);
}
