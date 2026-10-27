package com.revenda.vehicles.infrastructure.adapters.in.web;

/** Só a página, para a listagem sem filtros. */
record PageParams(@Paged.PageNumber Integer page, @Paged.PageSize Integer size) implements Paged {
}
