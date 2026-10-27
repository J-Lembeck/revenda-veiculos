package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.common.money.Money;
import com.revenda.common.text.Texts;
import com.revenda.vehicles.domain.model.ShowcaseFilter;
import com.revenda.vehicles.domain.model.VehicleDetails;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A consulta da vitrine: os filtros opcionais e a página. */
record ShowcaseParams(
        @Parameter(description = "Marca, o nome inteiro, sem diferença de maiúsculas", example = "Chevrolet")
        @Size(max = VehicleDetails.BRAND_LIMIT) String brand,
        @Parameter(description = "Modelo, o nome inteiro, sem diferença de maiúsculas", example = "Onix")
        @Size(max = VehicleDetails.MODEL_LIMIT) String model,
        @Parameter(description = "Preço mínimo em centavos, incluído", example = "5000000")
        @PositiveOrZero Long minPriceCents,
        @Parameter(description = "Preço máximo em centavos, incluído", example = "12000000")
        @PositiveOrZero Long maxPriceCents,
        @Parameter(description = "Ano do modelo mínimo, incluído", example = "2020") @PositiveOrZero Integer minYear,
        @Parameter(description = "Ano do modelo máximo, incluído", example = "2025") @PositiveOrZero Integer maxYear,
        @Parameter(description = "Página, de 0 em diante (padrão 0)", example = "0") @Paged.PageNumber Integer page,
        @Parameter(description = "Itens por página, de 1 a 100 (padrão 20)", example = "20")
        @Paged.PageSize Integer size) implements Paged {

    ShowcaseFilter toFilter() {
        return new ShowcaseFilter(Texts.blankToNull(brand), Texts.blankToNull(model), cents(minPriceCents),
                cents(maxPriceCents), minYear, maxYear);
    }

    private static Money cents(Long value) {
        return value == null ? null : Money.ofCents(value);
    }
}
