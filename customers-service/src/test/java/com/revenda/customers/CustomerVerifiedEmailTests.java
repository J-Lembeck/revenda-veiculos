package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.SyntheticData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** A conferência do e-mail ligada, como na nuvem ({@code CUSTOMERS_REQUIRE_VERIFIED_EMAIL=true}). */
@TestPropertySource(properties = "revenda.customers.require-verified-email=true")
class CustomerVerifiedEmailTests extends CustomersApiTest {

    @Test
    void anAccountWithTheEmailVerifiedRegisters() {
        ResponseEntity<JsonNode> response = as(newAccount()).post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void anAccountWithTheEmailNotVerifiedIsRefusedAndNothingIsSaved() {
        Account unverified = newAccount().withEmail(SyntheticData.email(), false);
        int auditBefore = auditRows();

        ResponseEntity<JsonNode> response = as(unverified).post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(response.getBody().get("message").asString()).startsWith("Confirme o e-mail da sua conta");
        assertThat(customersOf(unverified)).isZero();
        assertThat(auditRows()).isEqualTo(auditBefore);
    }

    @Test
    void aTokenWithoutTheVerifiedMarkCountsAsNotVerified() {
        Account withoutMark = newAccount().withEmail(SyntheticData.email(), null);

        ResponseEntity<JsonNode> response = as(withoutMark).post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("EMAIL_NOT_VERIFIED");
    }
}
