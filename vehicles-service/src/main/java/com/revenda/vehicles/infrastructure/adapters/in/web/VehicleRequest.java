package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.common.money.Money;
import com.revenda.vehicles.domain.model.VehicleDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record VehicleRequest(
        @NotBlank @Size(max = VehicleDetails.BRAND_LIMIT) String brand,
        @NotBlank @Size(max = VehicleDetails.MODEL_LIMIT) String model,
        @Size(max = VehicleDetails.TRIM_NAME_LIMIT) String trimName,
        @NotNull Integer manufactureYear,
        @NotNull Integer modelYear,
        @NotBlank @Size(max = VehicleDetails.COLOR_LIMIT) String color,
        @NotNull Integer mileageKm,
        @NotBlank String plate,
        @NotBlank String vin,
        @NotBlank String renavam,
        @NotNull Long priceCents,
        @NotNull(groups = Edit.class) Integer version) {

    /** As regras que só valem no {@code PUT}: a edição traz a {@code version} lida. */
    interface Edit {
    }

    VehicleDetails toDetails() {
        return new VehicleDetails(brand.strip(), model.strip(), blankToNull(trimName), manufactureYear, modelYear,
                color.strip(), mileageKm, plate, vin, renavam, Money.ofCents(priceCents));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
