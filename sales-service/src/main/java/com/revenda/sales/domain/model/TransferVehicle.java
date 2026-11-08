package com.revenda.sales.domain.model;

import java.util.Objects;
import java.util.UUID;

/** O veículo como o documento de transferência pede: a identificação do ATPV-e, lida do estoque na hora de emitir. */
public record TransferVehicle(UUID vehicleId, String brand, String model, String trimName, int manufactureYear,
        int modelYear, String color, int mileageKm, String plate, String vin, String renavam) {

    public TransferVehicle {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(vin, "vin");
        Objects.requireNonNull(renavam, "renavam");
    }
}
