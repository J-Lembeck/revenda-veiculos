package com.revenda.vehicles.domain.ports.in;

import java.util.Objects;
import java.util.UUID;

public record ReleaseVehicleCommand(UUID messageId, UUID vehicleId, UUID purchaseId) {

    public ReleaseVehicleCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(purchaseId, "purchaseId");
    }
}
