package com.revenda.vehicles.infrastructure.adapters.in.web;

final class Texts {

    private Texts() {
    }

    /** O texto sem espaços nas pontas, ou nulo se veio vazio. */
    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
