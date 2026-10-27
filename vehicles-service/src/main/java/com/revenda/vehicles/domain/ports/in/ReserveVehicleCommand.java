package com.revenda.vehicles.domain.ports.in;

import java.util.UUID;

public record ReserveVehicleCommand(UUID vehicleId, UUID purchaseId) {
}
