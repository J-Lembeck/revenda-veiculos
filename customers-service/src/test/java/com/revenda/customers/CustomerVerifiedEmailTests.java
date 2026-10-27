package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.SyntheticData;
import java.util.Map;
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

    @Test
    void anEditKeepsTheStoredEmailUntilTheNewOneIsVerified() {
        Account account = newAccount();
        register(account, customerBody());
        String newEmail = "nova." + account.email();

        ResponseEntity<JsonNode> unverified = as(account.withEmail(newEmail, false)).patch(ME,
                Map.of("phone", "+5511988887777"));
        ResponseEntity<JsonNode> verified = as(account.withEmail(newEmail, true)).patch(ME, Map.of());

        assertThat(unverified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unverified.getBody().get("emailMasked").asString()).isEqualTo("p***@example.test");
        assertThat(unverified.getBody().get("phoneMasked").asString()).isEqualTo("(11) *****-7777");
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getBody().get("emailMasked").asString()).isEqualTo("n***@example.test");
    }
}
