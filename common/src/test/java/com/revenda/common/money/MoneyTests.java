package com.revenda.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTests {

    @Test
    void isBornWithTwoPlacesRoundedHalfUp() {
        assertThat(Money.of("89900.005").amount()).isEqualTo(new BigDecimal("89900.01"));
        assertThat(Money.of("89900.004").amount()).isEqualTo(new BigDecimal("89900.00"));
        assertThat(Money.of("-0.005").amount()).isEqualTo(new BigDecimal("-0.01"));
        assertThat(Money.of("75000")).isEqualTo(Money.of("75000.00")).hasToString("75000.00");
        assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crossesTheBoundaryAsWholeCents() {
        assertThat(Money.ofCents(8_990_000)).isEqualTo(Money.of("89900.00"));
        assertThat(Money.of("89900.00").toCents()).isEqualTo(8_990_000);
        assertThat(Money.of("10000000.00").toCents()).isEqualTo(1_000_000_000);
        assertThat(Money.ofCents(-5).toCents()).isEqualTo(-5);
        assertThat(Money.ZERO.toCents()).isZero();
    }

    @Test
    void refusesCentsThatDoNotFitInALong() {
        Money huge = Money.of(new BigDecimal(Long.MAX_VALUE).add(BigDecimal.ONE));

        assertThatThrownBy(huge::toCents).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void addsSubtractsAndCompares() {
        Money price = Money.of("89900.00");
        Money paid = Money.of("89899.99");

        assertThat(price.plus(Money.of("100.00"))).isEqualTo(Money.of("90000.00"));
        assertThat(paid.minus(price)).isEqualTo(Money.of("-0.01"));
        assertThat(price.isGreaterThan(paid)).isTrue();
        assertThat(price.isLessThan(paid)).isFalse();
        assertThat(price.compareTo(Money.of("89900"))).isZero();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(price.isPositive()).isTrue();
        assertThat(paid.minus(price).isNegative()).isTrue();
    }

    @Test
    void formatsForPeopleInBrazilianReal() {
        assertThat(MoneyText.of(Money.of("89900.00"))).isEqualTo("R$ 89.900,00");
        assertThat(MoneyText.of(Money.of("10000000"))).isEqualTo("R$ 10.000.000,00");
        assertThat(MoneyText.of(Money.ZERO)).isEqualTo("R$ 0,00");
    }
}
