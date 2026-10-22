package com.revenda.common.money;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyText {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private MoneyText() {
    }

    public static String of(Money value) {
        return "R$ " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR)).format(value.amount());
    }
}
