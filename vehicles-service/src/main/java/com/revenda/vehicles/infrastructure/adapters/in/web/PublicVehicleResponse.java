package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import java.util.UUID;

/** O veículo como o público vê na vitrine: sem placa, chassi nem RENAVAM. */
record PublicVehicleResponse(
        UUID id,
        String brand,
        String model,
        String trimName,
        int manufactureYear,
        int modelYear,
        String color,
        int mileageKm,
        long priceCents) {

    static PublicVehicleResponse of(Vehicle vehicle) {
        VehicleDetails details = vehicle.details();
        return new PublicVehicleResponse(vehicle.id(), details.brand(), details.model(), details.trimName(),
                details.manufactureYear(), details.modelYear(), details.color(), details.mileageKm(),
                details.price().toCents());
    }
}
