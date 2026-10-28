package com.revenda.payments.domain.model;

import com.revenda.common.money.Money;
import java.nio.charset.StandardCharsets;

/**
 * O Pix "copia e cola" no formato BR Code (EMV) do Banco Central, com uma chave fictícia: parece o de verdade e vira
 * QR Code, mas nenhum banco paga. Cada campo é {@code id + tamanho com dois dígitos + valor}; o último é o CRC16.
 */
public final class PixCode {

    static final String KEY = "pix@example.test";
    static final String MERCHANT = "REVENDA SIMULADA";
    static final String CITY = "SAO PAULO";
    private static final int TXID_LIMIT = 25;

    private PixCode() {
    }

    /** O código da cobrança {@code chargeId}, de uso único, com o valor e o identificador da transação. */
    public static String of(String chargeId, Money amount) {
        String txid = chargeId.replaceAll("[^A-Za-z0-9]", "");
        if (txid.length() > TXID_LIMIT) {
            txid = txid.substring(txid.length() - TXID_LIMIT);
        }
        String payload = field("00", "01")
                + field("01", "12")
                + field("26", field("00", "br.gov.bcb.pix") + field("01", KEY))
                + field("52", "0000")
                + field("53", "986")
                + field("54", amount.amount().toPlainString())
                + field("58", "BR")
                + field("59", MERCHANT)
                + field("60", CITY)
                + field("62", field("05", txid))
                + "6304";
        return payload + crc16(payload);
    }

    static String field(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    /** CRC16-CCITT (polinômio 0x1021, início 0xFFFF), em quatro dígitos hexadecimais maiúsculos. */
    static String crc16(String text) {
        int crc = 0xFFFF;
        for (byte octet : text.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (octet & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            }
        }
        return String.format("%04X", crc & 0xFFFF);
    }
}
