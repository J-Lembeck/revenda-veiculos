package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import java.time.Instant;
import java.util.UUID;

/** O veículo como a equipe vê: com placa, chassi e RENAVAM. */
record StaffVehicleResponse(
        UUID id,
        String brand,
        String model,
        String trimName,
        int manufactureYear,
        int modelYear,
        String color,
        int mileageKm,
        String plate,
        String vin,
        String renavam,
        long priceCents,
        String status,
        Long soldPriceCents,
        Instant soldAt,
        Instant deliveredAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    static StaffVehicleResponse of(Vehicle vehicle) {
        VehicleDetails details = vehicle.details();
        return new StaffVehicleResponse(vehicle.id(), details.brand(), details.model(), details.trimName(),
                details.manufactureYear(), details.modelYear(), details.color(), details.mileageKm(), details.plate(),
                details.vin(), details.renavam(), details.price().toCents(), vehicle.status().name(),
                vehicle.soldPrice() == null ? null : vehicle.soldPrice().toCents(), vehicle.soldAt(),
                vehicle.deliveredAt(), vehicle.version(), vehicle.createdAt(), vehicle.updatedAt());
    }
}
