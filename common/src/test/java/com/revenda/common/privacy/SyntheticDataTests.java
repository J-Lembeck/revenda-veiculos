package com.revenda.common.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.test.SyntheticData;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class SyntheticDataTests {

    @Test
    void completesTheBaseWithTheKnownCheckDigits() {
        assertThat(SyntheticData.cpfFromBase("123456789")).isEqualTo("12345678909");
        assertThat(SyntheticData.cpfFromBase("111444777")).isEqualTo("11144477735");
        assertThat(SyntheticData.cpfFromBase("000000001")).isEqualTo("00000000191");
    }

    @RepeatedTest(200)
    void everyGeneratedCpfHasValidCheckDigits() {
        String cpf = SyntheticData.cpf();

        assertThat(cpf).matches("\\d{11}");
        assertThat(cpf.chars().distinct().count()).isGreaterThan(1);
        assertThat(cpf.charAt(9) - '0').isEqualTo(checkDigitByTheOtherFormula(cpf.substring(0, 9)));
        assertThat(cpf.charAt(10) - '0').isEqualTo(checkDigitByTheOtherFormula(cpf.substring(0, 10)));
    }

    @Test
    void generatedCpfsVary() {
        Set<String> cpfs = new HashSet<>();
        IntStream.range(0, 50).forEach(i -> cpfs.add(SyntheticData.cpf()));

        assertThat(cpfs).hasSizeGreaterThan(45);
    }

    @Test
    void refusesABaseThatIsNotNineDigits() {
        assertThatThrownBy(() -> SyntheticData.cpfFromBase("12345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyntheticData.cpfFromBase("12345678a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsWithDotsAndDash() {
        assertThat(SyntheticData.formatCpf("12345678909")).isEqualTo("123.456.789-09");
    }

    @Test
    void generatedCpfIsMaskedInBothFormats() {
        String cpf = SyntheticData.cpf();
        String formatted = SyntheticData.formatCpf(cpf);

        assertThat(PiiMasker.mask("cpf " + cpf)).doesNotContain(cpf);
        assertThat(PiiMasker.mask("cpf " + formatted)).doesNotContain(formatted);
    }

    @Test
    void emailIsAlwaysInTheReservedTestDomain() {
        String email = SyntheticData.email();

        assertThat(email).matches("pessoa\\.[0-9a-f]{12}@example\\.test");
        assertThat(SyntheticData.email()).isNotEqualTo(email);
        assertThat(PiiMasker.mask(email)).doesNotContain(email);
    }

    @Test
    void completesTheRenavamBaseWithTheKnownCheckDigit() {
        assertThat(SyntheticData.renavamFromBase("0063988496")).isEqualTo("00639884962");
        assertThat(SyntheticData.renavamFromBase("0000000001")).isEqualTo("00000000019");
        assertThat(SyntheticData.renavamFromBase("0000000000")).isEqualTo("00000000000");
    }

    @RepeatedTest(200)
    void everyGeneratedRenavamHasAValidCheckDigit() {
        String renavam = SyntheticData.renavam();

        assertThat(renavam).matches("\\d{11}");
        assertThat(renavam.charAt(10) - '0').isEqualTo(renavamDigitByTheOtherFormula(renavam.substring(0, 10)));
    }

    @Test
    void refusesARenavamBaseThatIsNotTenDigits() {
        assertThatThrownBy(() -> SyntheticData.renavamFromBase("123456789")).isInstanceOf(IllegalArgumentException.class);
    }

    @RepeatedTest(50)
    void plateAndVinFollowTheStockFormat() {
        assertThat(SyntheticData.plate()).matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}");
        assertThat(SyntheticData.vin()).matches("[A-HJ-NPR-Z0-9]{17}");
    }

    private static int renavamDigitByTheOtherFormula(String base) {
        int[] weights = {3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (base.charAt(i) - '0') * weights[i];
        }
        return sum * 10 % 11 % 10;
    }

    private static int checkDigitByTheOtherFormula(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * (digits.length() + 1 - i);
        }
        return sum * 10 % 11 % 10;
    }
}
