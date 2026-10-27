package com.revenda.vehicles.domain.model;

import com.revenda.common.money.Money;

/**
 * Os filtros opcionais da vitrine (RN-03): marca e modelo exatos sem diferença de maiúsculas, faixa de preço e
 * faixa do ano do modelo, com as pontas incluídas. Filtro nulo não restringe.
 */
public record ShowcaseFilter(
        String brand,
        String model,
        Money minPrice,
        Money maxPrice,
        Integer minYear,
        Integer maxYear) {

    public static final ShowcaseFilter NONE = new ShowcaseFilter(null, null, null, null, null, null);
}
