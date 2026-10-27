package com.revenda.vehicles.domain.ports.in;

import com.revenda.vehicles.domain.model.VehicleDetails;

public record RegisterVehicleCommand(VehicleDetails vehicle) {
}
