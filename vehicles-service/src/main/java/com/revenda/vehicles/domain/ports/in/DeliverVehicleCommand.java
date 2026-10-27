package com.revenda.vehicles.domain.ports.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** {@code deliveredAt} é quando a retirada foi confirmada na loja. */
public record DeliverVehicleCommand(UUID messageId, UUID vehicleId, UUID purchaseId, Instant deliveredAt) {

    public DeliverVehicleCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
    }
}
