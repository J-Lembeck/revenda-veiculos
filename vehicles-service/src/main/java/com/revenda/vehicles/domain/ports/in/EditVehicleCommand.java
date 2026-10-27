package com.revenda.vehicles.domain.ports.in;

import com.revenda.vehicles.domain.model.VehicleDetails;
import java.util.UUID;

public record EditVehicleCommand(UUID id, VehicleDetails vehicle, int version) {
}
