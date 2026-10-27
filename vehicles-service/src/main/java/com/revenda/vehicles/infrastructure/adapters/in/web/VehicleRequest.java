package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.common.money.Money;
import com.revenda.vehicles.domain.model.VehicleDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "O veículo inteiro, no cadastro e na edição; a edição traz também a `version` lida.")
record VehicleRequest(
        @Schema(example = "Chevrolet") @NotBlank @Size(max = VehicleDetails.BRAND_LIMIT) String brand,
        @Schema(example = "Onix") @NotBlank @Size(max = VehicleDetails.MODEL_LIMIT) String model,
        @Schema(description = "Versão de acabamento, opcional", example = "1.0 Turbo LT")
        @Size(max = VehicleDetails.TRIM_NAME_LIMIT) String trimName,
        @Schema(description = "De 1900 ao ano que vem", example = "2023") @NotNull Integer manufactureYear,
        @Schema(description = "O de fabricação ou o seguinte", example = "2024") @NotNull Integer modelYear,
        @Schema(example = "Prata") @NotBlank @Size(max = VehicleDetails.COLOR_LIMIT) String color,
        @Schema(description = "Zero ou mais", example = "18500") @NotNull Integer mileageKm,
        @Schema(description = "Padrão antigo ou Mercosul, maiúsculas, sem hífen", example = "BRA2E19")
        @NotBlank String plate,
        @Schema(description = "Chassi: 17 caracteres, sem I, O e Q", example = "9BGEA48A0PG000001")
        @NotBlank String vin,
        @Schema(description = "11 dígitos com o verificador", example = "00000000019") @NotBlank String renavam,
        @Schema(description = "Em centavos, acima de zero e até 1000000000 (R$ 10.000.000,00)", example = "8990000")
        @NotNull Long priceCents,
        @Schema(description = "Obrigatória só na edição: a versão lida no detalhe", example = "0",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @NotNull(groups = Edit.class) Integer version) {

    /** As regras que só valem no {@code PUT}: a edição traz a {@code version} lida. */
    interface Edit {
    }

    VehicleDetails toDetails() {
        return new VehicleDetails(brand.strip(), model.strip(), Texts.blankToNull(trimName), manufactureYear,
                modelYear, color.strip(), mileageKm, plate, vin, renavam, Money.ofCents(priceCents));
    }
}
