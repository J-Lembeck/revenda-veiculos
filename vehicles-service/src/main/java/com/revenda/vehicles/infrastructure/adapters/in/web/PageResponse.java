package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Page;
import java.util.List;
import java.util.function.Function;

record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

    static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        Page<T> mapped = page.map(mapper);
        return new PageResponse<>(mapped.items(), mapped.page(), mapped.size(), mapped.totalItems());
    }
}
