package com.revenda.common.paging.web;

import io.swagger.v3.oas.annotations.Parameter;

/** Só a página, para a listagem sem filtros. */
public record PageParams(
        @Parameter(description = "Página, de 0 em diante (padrão 0)", example = "0") @Paged.PageNumber Integer page,
        @Parameter(description = "Itens por página, de 1 a 100 (padrão 20)", example = "20")
        @Paged.PageSize Integer size) implements Paged {
}
