package com.revenda.customers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.FieldViolation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerDetailsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final String CPF = "52998224725";
    private static final Address ADDRESS = new Address("01001000", "Praça da Sé", "100", "apto 1", "Sé", "São Paulo",
            "SP");

    @Test
    void validDetailsKeepTheCpfOnlyAsDigitsAndTheNameWithoutOuterSpaces() {
        CustomerDetails details = CustomerDetails.of("  Maria Exemplo  ", "529.982.247-25", LocalDate.of(1990, 5, 17),
                "+5511999990000", ADDRESS, TODAY);

        assertThat(details.cpf().digits()).isEqualTo(CPF);
        assertThat(details.fullName()).isEqualTo("Maria Exemplo");
        assertThat(details.phone().number()).isEqualTo("+5511999990000");
    }

    @Test
    void theBirthDateGoesFrom1900UntilYesterday() {
        assertThat(CustomerDetails.of("Maria", CPF, LocalDate.of(1900, 1, 1), "+5511999990000", ADDRESS, TODAY))
                .isNotNull();
        assertThat(CustomerDetails.of("Maria", CPF, TODAY.minusDays(1), "+5511999990000", ADDRESS, TODAY))
                .isNotNull();
        assertThatThrownBy(() -> CustomerDetails.of("Maria", CPF, TODAY, "+5511999990000", ADDRESS, TODAY))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CustomerDetails.of("Maria", CPF, LocalDate.of(1899, 12, 31), "+5511999990000",
                ADDRESS, TODAY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aMinorIsAcceptedBecauseTheAgeIsCheckedOnlyAtThePurchase() {
        assertThat(CustomerDetails.of("Caio", CPF, TODAY.minusYears(16), "+5511999990000", ADDRESS, TODAY))
                .isNotNull();
    }

    @Test
    void theCpfAndTheBirthDateAreRefusedTogether() {
        assertThatThrownBy(() -> CustomerDetails.of("Maria", "12345678900", TODAY.plusDays(1), "+5511999990000",
                ADDRESS, TODAY))
                .isInstanceOfSatisfying(BusinessException.class, refused -> {
                    assertThat(refused.getCode()).isEqualTo("VALIDATION_FAILED");
                    assertThat(refused.getFields()).extracting(FieldViolation::field, FieldViolation::code)
                            .containsExactlyInAnyOrder(tuple("cpf", "CPF_INVALID"), tuple("birthDate", "DATE_INVALID"));
                    assertThat(refused.getMessage()).doesNotContain("12345678900");
                });
    }

    @Test
    void aNewCustomerIsActiveInVersionZeroAndNeverShowsItsData() {
        CustomerDetails details = CustomerDetails.of("Maria", CPF, LocalDate.of(1990, 5, 17), "+5511999990000",
                ADDRESS, TODAY);
        Instant now = Instant.parse("2026-10-27T12:00:00Z");

        Customer customer = Customer.register(UUID.randomUUID(), "sub-maria", new EmailAddress("maria@example.test"),
                details, now);

        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.version()).isZero();
        assertThat(customer.createdAt()).isEqualTo(now).isEqualTo(customer.updatedAt());
        assertThat(customer.toString() + details).doesNotContain(CPF, "982.247", "maria@", "Praça", "sub-maria");
    }
}
