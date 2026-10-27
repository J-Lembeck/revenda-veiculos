package com.revenda.common.text;

public final class Texts {

    private Texts() {
    }

    /** O texto sem espaços nas pontas, ou nulo se veio vazio. */
    public static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
