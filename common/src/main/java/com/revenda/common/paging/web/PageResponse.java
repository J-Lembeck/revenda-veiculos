package com.revenda.common.paging.web;

import com.revenda.common.paging.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

/** A página como sai no JSON: {@code { items, page, size, totalItems }}. */
public record PageResponse<T>(
        List<T> items,
        @Schema(description = "A página devolvida, de 0 em diante", example = "0") int page,
        @Schema(description = "Itens por página pedidos", example = "20") int size,
        @Schema(description = "Total de itens em todas as páginas", example = "137") long totalItems) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        Page<T> mapped = page.map(mapper);
        return new PageResponse<>(mapped.items(), mapped.page(), mapped.size(), mapped.totalItems());
    }
}
