package com.revenda.vehicles.domain.model;

/** Qual página de uma listagem: a partir de 0, com até {@link #MAX_SIZE} itens. */
public record Paging(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public Paging {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Página fora dos limites: page=" + page + ", size=" + size);
        }
    }

    /** A página pedida, ou a primeira com {@link #DEFAULT_SIZE} itens no que não veio. */
    public static Paging of(Integer page, Integer size) {
        return new Paging(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    public long offset() {
        return (long) page * size;
    }
}
