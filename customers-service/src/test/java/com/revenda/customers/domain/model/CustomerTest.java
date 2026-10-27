package com.revenda.customers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant EDITED_AT = REGISTERED_AT.plusSeconds(60);
    private static final Address ADDRESS = new Address("01001000", "Praça da Sé", "100", "apto 1", "Sé", "São Paulo",
            "SP");
    private static final Address OTHER_ADDRESS = new Address("04538133", "Avenida dos Testes", "2000", null, "Itaim",
            "São Paulo", "SP");
    private static final Customer CUSTOMER = Customer.register(UUID.randomUUID(), "conta-da-maria",
            new EmailAddress("maria@example.test"), CustomerDetails.of("Maria Exemplo", "52998224725",
                    LocalDate.of(1990, 5, 17), "+5511999990000", ADDRESS, LocalDate.of(2026, 10, 27)),
            REGISTERED_AT);

    @Test
    void anEditChangesOnlyWhatCameAndMovesToTheNextVersion() {
        Customer edited = CUSTOMER.edit(null, new Phone("+5511988887777"), null, null, EDITED_AT);

        assertThat(edited.details().phone().number()).isEqualTo("+5511988887777");
        assertThat(edited.details().fullName()).isEqualTo("Maria Exemplo");
        assertThat(edited.details().address()).isEqualTo(ADDRESS);
        assertThat(edited.details().cpf()).isEqualTo(CUSTOMER.details().cpf());
        assertThat(edited.details().birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(edited.email()).isEqualTo(CUSTOMER.email());
        assertThat(edited.version()).isEqualTo(1);
        assertThat(edited.createdAt()).isEqualTo(REGISTERED_AT);
        assertThat(edited.updatedAt()).isEqualTo(EDITED_AT);
        assertThat(CUSTOMER.changesTo(edited)).containsExactly(CustomerField.PHONE);
    }

    @Test
    void everyFieldThatChangesIsListedAndTheNameLosesItsOuterSpaces() {
        Customer edited = CUSTOMER.edit("  Maria Souza ", new Phone("+5511988887777"), OTHER_ADDRESS,
                new EmailAddress("maria.nova@example.test"), EDITED_AT);

        assertThat(edited.details().fullName()).isEqualTo("Maria Souza");
        assertThat(edited.email().address()).isEqualTo("maria.nova@example.test");
        assertThat(CUSTOMER.changesTo(edited)).containsExactly(CustomerField.FULL_NAME, CustomerField.PHONE,
                CustomerField.ADDRESS, CustomerField.EMAIL);
    }

    @Test
    void withoutAnyChangeTheSameCustomerComesBackInTheSameVersion() {
        Customer sameValues = CUSTOMER.edit(" Maria Exemplo ", new Phone("+5511999990000"), ADDRESS,
                new EmailAddress("maria@example.test"), EDITED_AT);

        assertThat(CUSTOMER.edit(null, null, null, null, EDITED_AT)).isSameAs(CUSTOMER);
        assertThat(sameValues).isSameAs(CUSTOMER);
        assertThat(CUSTOMER.changesTo(sameValues)).isEmpty();
    }
}
