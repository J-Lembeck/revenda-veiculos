package com.revenda.payments.application.service;

import java.util.UUID;

/** Os ids que o gateway dá: {@code chg_…} para a cobrança e {@code evt_…} para o evento do webhook. */
final class GatewayIds {

    private GatewayIds() {
    }

    static String charge() {
        return "chg_" + random();
    }

    static String event() {
        return "evt_" + random();
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
