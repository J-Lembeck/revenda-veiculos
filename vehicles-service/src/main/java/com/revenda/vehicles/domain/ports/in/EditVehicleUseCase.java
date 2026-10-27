package com.revenda.vehicles.domain.ports.in;

import com.revenda.vehicles.domain.model.Vehicle;

public interface EditVehicleUseCase {

    Vehicle edit(EditVehicleCommand command);
}
