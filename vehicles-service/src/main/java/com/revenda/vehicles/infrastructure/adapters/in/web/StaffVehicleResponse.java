package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** O veículo como a equipe vê: com placa, chassi e RENAVAM. */
@Schema(description = "O veículo como a equipe vê: com placa, chassi, RENAVAM, status, venda e versão.")
record StaffVehicleResponse(
        UUID id,
        @Schema(example = "Chevrolet") String brand,
        @Schema(example = "Onix") String model,
        @Schema(example = "1.0 Turbo LT", nullable = true) String trimName,
        @Schema(example = "2023") int manufactureYear,
        @Schema(example = "2024") int modelYear,
        @Schema(example = "Prata") String color,
        @Schema(example = "18500") int mileageKm,
        @Schema(example = "BRA2E19") String plate,
        @Schema(example = "9BGEA48A0PG000001") String vin,
        @Schema(example = "00000000019") String renavam,
        @Schema(description = "Preço atual em centavos", example = "8990000") long priceCents,
        @Schema(allowableValues = {"AVAILABLE", "RESERVED", "SOLD", "DELIVERED"}, example = "AVAILABLE")
        String status,
        @Schema(description = "Preço da venda em centavos — o fixado na reserva; nulo antes da venda",
                example = "8990000", nullable = true) Long soldPriceCents,
        @Schema(description = "Quando o pagamento foi confirmado", nullable = true) Instant soldAt,
        @Schema(description = "Quando a retirada foi confirmada", nullable = true) Instant deliveredAt,
        @Schema(description = "Versão para a próxima edição", example = "0") int version,
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
