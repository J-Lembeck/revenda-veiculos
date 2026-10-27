package com.revenda.vehicles.application.service;

import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;

record VehicleSnapshot(
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
        int version) {

    static VehicleSnapshot of(Vehicle vehicle) {
        VehicleDetails details = vehicle.details();
        return new VehicleSnapshot(details.brand(), details.model(), details.trimName(), details.manufactureYear(),
                details.modelYear(), details.color(), details.mileageKm(), details.plate(), details.vin(),
                details.renavam(), details.price().toCents(), vehicle.status().name(), vehicle.version());
    }
}
