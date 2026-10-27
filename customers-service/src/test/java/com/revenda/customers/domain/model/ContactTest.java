package com.revenda.customers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContactTest {

    @ParameterizedTest
    @CsvSource({
            "+5511999990000, (11) *****-0000",
            "+551133334444, (11) ****-4444",
            "+5521912345678, (21) *****-5678"})
    void thePhoneShowsOnlyTheAreaAndTheLastFourDigits(String number, String masked) {
        Phone phone = new Phone(number);

        assertThat(phone.masked()).isEqualTo(masked);
        assertThat(phone).hasToString(masked);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"11999990000", "+55 11 99999-0000", "+5501999990000", "+55119999900", "+55119999900001",
            "+5411999990000", "+5511899990000"})
    void aPhoneOutOfTheFormatCannotBeBuilt(String number) {
        assertThatThrownBy(() -> new Phone(number)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theEmailShowsOnlyTheFirstLetterAndTheDomain() {
        EmailAddress email = new EmailAddress("maria.silva@example.test");

        assertThat(email.masked()).isEqualTo("m***@example.test");
        assertThat(email).hasToString("m***@example.test");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "maria", "@example.test", "maria@", "maria@@example.test", "a@b@example.test"})
    void anEmailWithoutUserOrDomainIsNotUsable(String address) {
        assertThat(EmailAddress.isUsable(address)).isFalse();
        assertThatThrownBy(() -> new EmailAddress(address)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAddressNeverShowsItself() {
        Address address = new Address("01001000", "Praça da Sé", "100", null, "Sé", "São Paulo", "SP");

        assertThat(address.toString()).doesNotContain("01001000", "Praça", "São Paulo");
    }
}
