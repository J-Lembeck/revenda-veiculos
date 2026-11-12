package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Eliminação: anonimiza na hora sem venda no prazo de guarda; com venda, agenda para o fim do prazo. */
class CustomerDeletionTests extends CustomersApiTest {

    private static final String DELETION = ME + "/deletion";
    private static final String[] PERSONAL = {"identity_subject", "full_name", "cpf_enc", "cpf_index", "email_enc",
            "phone_enc", "birth_date_enc", "address_enc"};

    @Autowired
    private MeterRegistry meters;

    @Test
    void withoutSaleTheProfileIsAnonymizedRightAway() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        double before = anonymizations("request");

        ResponseEntity<JsonNode> answer = as(account).post(DELETION, null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getBody().get("status").asString()).isEqualTo("ANONYMIZED");
        Map<String, Object> row = rowOf(id);
        assertThat(row.get("status")).isEqualTo("ANONYMIZED");
        assertThat(row.get("anonymized_at")).isNotNull();
        for (String column : PERSONAL) {
            assertThat(row.get(column)).as(column).isNull();
        }
        assertThat(audited("CUSTOMER_DELETION_REQUESTED", id)).isEqualTo(1);
        assertThat(audited("CUSTOMER_ANONYMIZED", id)).isEqualTo(1);
        assertThat(anonymizations("request")).isEqualTo(before + 1);
        assertThat(as(account).get(ME).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(SALES.retentionCallsFor(id)).hasSize(1).allMatch(FakeSales.Call::withServiceToken);
    }

    @Test
    void theAnonymizedAccountCanRegisterAgainWithTheSameCpf() {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        register(account, body);
        as(account).post(DELETION, null);

        ResponseEntity<JsonNode> again = as(account).post(CUSTOMERS, body);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(as(account).post(DELETION, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withASaleInTheRetentionPeriodTheRequestIsScheduled() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        SALES.retains(id, Instant.parse("2031-11-08T15:00:00Z"));

        ResponseEntity<JsonNode> answer = as(account).post(DELETION, null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getBody().get("code").asString()).isEqualTo("CUSTOMER_HAS_RETAINED_DATA");
        assertThat(answer.getBody().get("message").asString()).contains("08/11/2031");
        Map<String, Object> row = rowOf(id);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("deletion_requested_at")).isNotNull();
        assertThat(audited("CUSTOMER_DELETION_REQUESTED", id)).isEqualTo(1);
        assertThat(audited("CUSTOMER_ANONYMIZED", id)).isZero();
        assertThat(as(account).get(ME + "/export").getBody().get("customer").get("deletionRequestedAt").isNull())
                .isFalse();
    }

    @Test
    void repeatingTheScheduledRequestChangesNothing() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        SALES.retains(id, Instant.now().plus(400, ChronoUnit.DAYS));
        as(account).post(DELETION, null);
        Map<String, Object> scheduled = rowOf(id);

        ResponseEntity<JsonNode> again = as(account).post(DELETION, null);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changedColumns(scheduled, rowOf(id))).isEmpty();
        assertThat(audited("CUSTOMER_DELETION_REQUESTED", id)).isEqualTo(1);
    }

    @Test
    void aRetentionPeriodAlreadyOverAnonymizesRightAway() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        SALES.retains(id, Instant.parse("2025-01-10T09:00:00Z"));

        assertThat(as(account).post(DELETION, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rowOf(id).get("status")).isEqualTo("ANONYMIZED");
    }

    @Test
    void withoutAnswerFromSalesNothingChanges() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);
        SALES.failsWith(503);

        ResponseEntity<JsonNode> answer = as(account).post(DELETION, null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(answer.getBody().get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(changedColumns(before, rowOf(id))).isEmpty();
        assertThat(audited("CUSTOMER_DELETION_REQUESTED", id)).isZero();
    }

    @Test
    void anAccountWithoutProfileGetsNotFoundWithoutAskingSales() {
        ResponseEntity<JsonNode> answer = as(newAccount()).post(DELETION, null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(answer.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
    }

    private double anonymizations(String trigger) {
        Counter counter = meters.find("customers.anonymizations").tag("trigger", trigger).counter();
        return counter == null ? 0 : counter.count();
    }
}
