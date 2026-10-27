package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** O veículo como o público vê na vitrine: sem placa, chassi nem RENAVAM. */
@Schema(description = "O veículo como o público vê: sem placa, chassi, RENAVAM, status nem versão.")
record PublicVehicleResponse(
        UUID id,
        @Schema(example = "Chevrolet") String brand,
        @Schema(example = "Onix") String model,
        @Schema(example = "1.0 Turbo LT", nullable = true) String trimName,
        @Schema(example = "2023") int manufactureYear,
        @Schema(example = "2024") int modelYear,
        @Schema(example = "Prata") String color,
        @Schema(example = "18500") int mileageKm,
        @Schema(description = "Em centavos", example = "8990000") long priceCents) {

    static PublicVehicleResponse of(Vehicle vehicle) {
        VehicleDetails details = vehicle.details();
        return new PublicVehicleResponse(vehicle.id(), details.brand(), details.model(), details.trimName(),
                details.manufactureYear(), details.modelYear(), details.color(), details.mileageKm(),
                details.price().toCents());
    }
}
