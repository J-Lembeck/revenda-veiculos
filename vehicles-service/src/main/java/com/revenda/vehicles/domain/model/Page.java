package com.revenda.vehicles.domain.model;

import java.util.List;
import java.util.function.Function;

public record Page<T>(List<T> items, int page, int size, long totalItems) {

    public Page {
        items = List.copyOf(items);
    }

    public static <T> Page<T> of(List<T> items, Paging paging, long totalItems) {
        return new Page<>(items, paging.page(), paging.size(), totalItems);
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), page, size, totalItems);
    }
}
