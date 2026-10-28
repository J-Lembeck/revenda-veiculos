package com.revenda.payments.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.money.Money;
import org.junit.jupiter.api.Test;

class PixCodeTest {

    private static final String CHARGE = "chg_0123456789abcdef0123456789abcdef";

    @Test
    void theCrcIsTheCcittOfTheBrCode() {
        assertThat(PixCode.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void theCodeIsTheBrCodeWithTheFictitiousKeyTheAmountAndTheTransactionId() {
        assertThat(PixCode.of(CHARGE, Money.ofCents(4_590_000))).isEqualTo("00020101021226380014br.gov.bcb.pix0116"
                + "pix@example.test520400005303986540845900.005802BR5916REVENDA SIMULADA6009SAO PAULO"
                + "62290525789abcdef0123456789abcdef6304EB29");
    }

    @Test
    void theLastFourCharactersCheckEverythingBefore() {
        String code = PixCode.of(CHARGE, Money.ofCents(1));
        String payload = code.substring(0, code.length() - 4);

        assertThat(payload).endsWith("6304").contains("54040.01");
        assertThat(code).endsWith(PixCode.crc16(payload));
    }

    @Test
    void theTransactionIdKeepsOnlyLettersAndDigitsUpToTwentyFive() {
        assertThat(PixCode.of("chg_abc", Money.ofCents(100))).contains("62100506chgabc6304");
        assertThat(PixCode.of("chg_" + "a".repeat(30), Money.ofCents(100))).contains("0525" + "a".repeat(25) + "6304");
    }
}
