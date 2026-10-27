package com.revenda.vehicles.domain.model;

import com.revenda.common.money.Money;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record Vehicle(
        UUID id,
        VehicleDetails details,
        VehicleStatus status,
        UUID purchaseId,
        Money soldPrice,
        Instant soldAt,
        Instant deliveredAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static final String ENTITY_TYPE = "VEHICLE";

    /** Veículo novo no estoque: à venda, sem compra, na versão 0. */
    public static Vehicle register(UUID id, VehicleDetails details, Instant now) {
        details.requireValid(now.atZone(ZoneOffset.UTC).getYear());
        return new Vehicle(id, details, VehicleStatus.AVAILABLE, null, null, null, null, 0, now, now);
    }
}
