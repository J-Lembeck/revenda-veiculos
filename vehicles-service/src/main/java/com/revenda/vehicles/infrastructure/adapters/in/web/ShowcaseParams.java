package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.common.money.Money;
import com.revenda.vehicles.domain.model.ShowcaseFilter;
import com.revenda.vehicles.domain.model.VehicleDetails;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A consulta da vitrine: os filtros opcionais e a página. */
record ShowcaseParams(
        @Size(max = VehicleDetails.BRAND_LIMIT) String brand,
        @Size(max = VehicleDetails.MODEL_LIMIT) String model,
        @PositiveOrZero Long minPriceCents,
        @PositiveOrZero Long maxPriceCents,
        @PositiveOrZero Integer minYear,
        @PositiveOrZero Integer maxYear,
        @Paged.PageNumber Integer page,
        @Paged.PageSize Integer size) implements Paged {

    ShowcaseFilter toFilter() {
        return new ShowcaseFilter(Texts.blankToNull(brand), Texts.blankToNull(model), cents(minPriceCents),
                cents(maxPriceCents), minYear, maxYear);
    }

    private static Money cents(Long value) {
        return value == null ? null : Money.ofCents(value);
    }
}
