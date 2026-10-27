package com.revenda.customers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.test.SyntheticData;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    @RepeatedTest(20)
    void aSyntheticCpfWithValidDigitsIsAccepted() {
        String cpf = SyntheticData.cpf();

        assertThat(Cpf.parse(cpf)).map(Cpf::digits).contains(cpf);
    }

    @Test
    void dotsAndDashAreTakenOut() {
        String cpf = SyntheticData.cpf();

        assertThat(Cpf.parse(SyntheticData.formatCpf(cpf))).map(Cpf::digits).contains(cpf);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "12345678900", "11111111111", "00000000000", "5299822472", "529982247250",
            "529.982.247-26", "52998224725 ", "529.98224725", "529-982-247.25", "5299822472a"})
    void aCpfThatDoesNotCheckIsRefused(String text) {
        assertThat(Cpf.parse(text)).isEmpty();
    }

    @Test
    void itShowsOnlyTheSixMiddleDigits() {
        Cpf cpf = Cpf.parse("52998224725").orElseThrow();

        assertThat(cpf.masked()).isEqualTo("***.982.247-**");
        assertThat(cpf).hasToString("***.982.247-**");
    }

    @Test
    void anInvalidCpfCannotBeBuilt() {
        assertThatThrownBy(() -> new Cpf("12345678900")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("12345678900");
    }
}
