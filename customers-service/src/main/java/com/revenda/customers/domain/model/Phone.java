package com.revenda.customers.domain.model;

import java.util.regex.Pattern;

/** Telefone brasileiro no formato internacional: {@code +55}, o DDD e 8 ou 9 dígitos. */
public record Phone(String number) {

    public static final String FORMAT = "\\+55[1-9]{2}9?[0-9]{8}";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);
    private static final int COUNTRY_AND_AREA = 5;
    private static final int VISIBLE = 4;

    public Phone {
        if (number == null || !PATTERN.matcher(number).matches()) {
            throw new IllegalArgumentException("Telefone fora do formato +55, DDD e número");
        }
    }

    /** {@code (11) *****-0000}: só o DDD e os quatro últimos dígitos. */
    public String masked() {
        String local = number.substring(COUNTRY_AND_AREA);
        return "(" + number.substring(3, COUNTRY_AND_AREA) + ") " + "*".repeat(local.length() - VISIBLE) + "-"
                + local.substring(local.length() - VISIBLE);
    }

    @Override
    public String toString() {
        return masked();
    }
}
