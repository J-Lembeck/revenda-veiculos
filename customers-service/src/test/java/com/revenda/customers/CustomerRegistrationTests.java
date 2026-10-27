package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.crypto.BlindIndexer;
import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.crypto.FieldRef;
import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.test.TestTokens;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

@ExtendWith(OutputCaptureExtension.class)
class CustomerRegistrationTests extends CustomersApiTest {

    private static final List<String> CIPHERED = List.of("cpf_enc", "email_enc", "phone_enc", "birth_date_enc",
            "address_enc");

    @Autowired
    private FieldCipher cipher;

    @Autowired
    private BlindIndexer indexer;

    @Test
    void theCustomerRegistersAndSeesCpfEmailAndPhoneMasked(CapturedOutput output) {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");

        ResponseEntity<JsonNode> response = as(account).post(CUSTOMERS, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/customers/me");
        JsonNode customer = response.getBody();
        assertThat(customer.get("fullName").asString()).isEqualTo("Maria Exemplo da Silva");
        assertThat(customer.get("cpfMasked").asString()).isEqualTo(maskedCpf(cpf));
        assertThat(customer.get("emailMasked").asString())
                .isEqualTo(account.email().charAt(0) + "***@example.test");
        assertThat(customer.get("phoneMasked").asString()).isEqualTo("(11) *****-0000");
        assertThat(customer.get("birthDate").asString()).isEqualTo("1990-05-17");
        assertThat(customer.at("/address/street").asString()).isEqualTo("Praça da Sé");
        assertThat(customer.at("/address/state").asString()).isEqualTo("SP");
        assertThat(customer.propertyNames()).doesNotContain("cpf", "email", "phone", "identitySubject");
        assertThat(customer.toString()).doesNotContain(cpf, account.email(), "+5511999990000");
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), account.email(), "+5511999990000");
    }

    @Test
    void thePersonalDataIsStoredCipheredBoundToItsRowAndTheCpfIsIndexedBlind() {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");

        UUID id = register(account, body);

        Map<String, Object> row = jdbc.sql("SELECT * FROM customer WHERE id = :id").param("id", id).query().singleRow();
        assertThat(row).containsEntry("identity_subject", account.subject())
                .containsEntry("full_name", "Maria Exemplo da Silva").containsEntry("status", "ACTIVE");
        for (String column : CIPHERED) {
            assertThat(new String((byte[]) row.get(column), StandardCharsets.ISO_8859_1)).as(column)
                    .doesNotContain(cpf, account.email(), "999990000", "1990-05-17", "Praça", "01001000");
        }
        assertThat((byte[]) row.get("cpf_index")).isEqualTo(indexer.index("cpf", cpf));
        assertThat(cipher.decrypt(new FieldRef("customer", "cpf_enc", id), (byte[]) row.get("cpf_enc"))).isEqualTo(cpf);
        assertThat(cipher.decrypt(new FieldRef("customer", "email_enc", id), (byte[]) row.get("email_enc")))
                .isEqualTo(account.email());
        assertThatThrownBy(() -> cipher.decrypt(new FieldRef("customer", "cpf_enc", UUID.randomUUID()),
                (byte[]) row.get("cpf_enc"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(new FieldRef("customer", "phone_enc", id),
                (byte[]) row.get("cpf_enc"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theRegistrationIsAuditedWithTheAccountAndNoPersonalData() {
        Account account = newAccount();
        Map<String, Object> body = customerBody();

        UUID id = register(account, body);

        Map<String, Object> audit = jdbc.sql("""
                        SELECT actor_type, actor_id, actor_role, entity_type, summary, data::text AS data
                          FROM audit_log WHERE action = 'CUSTOMER_REGISTERED' AND entity_id = :id""")
                .param("id", id)
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_id", account.subject())
                .containsEntry("actor_role", "CUSTOMER").containsEntry("entity_type", "CUSTOMER")
                .containsEntry("summary", "Cliente cadastrado");
        assertThat((String) audit.get("data")).isEqualTo("{\"after\": {\"status\": \"ACTIVE\", \"version\": 0}}")
                .doesNotContain((String) body.get("cpf"), account.email(), "Maria");
    }

    @Test
    void theEmailComesFromTheAccountAndNeverFromTheBody() {
        Account account = newAccount();
        Map<String, Object> body = customerBody(Map.of("email", "outra.pessoa@example.test"));

        UUID id = register(account, body);

        byte[] stored = jdbc.sql("SELECT email_enc FROM customer WHERE id = :id").param("id", id)
                .query(byte[].class).single();
        assertThat(cipher.decrypt(new FieldRef("customer", "email_enc", id), stored)).isEqualTo(account.email());
    }

    @Test
    void aCpfWithDotsAndDashIsTheSameCpf() {
        String cpf = SyntheticData.cpf();
        Account first = newAccount();
        UUID id = register(first, customerBody(Map.of("cpf", SyntheticData.formatCpf(cpf))));

        ResponseEntity<JsonNode> second = as(newAccount()).post(CUSTOMERS, customerBody(Map.of("cpf", cpf)));

        assertThat(jdbc.sql("SELECT cpf_index FROM customer WHERE id = :id").param("id", id).query(byte[].class).single())
                .isEqualTo(indexer.index("cpf", cpf));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code").asString()).isEqualTo("CPF_ALREADY_REGISTERED");
    }

    @Test
    void aMinorRegistersBecauseTheAgeCountsOnlyAtThePurchase() {
        String sixteenYearsAgo = LocalDate.now(ZoneOffset.UTC).minusYears(16).toString();

        ResponseEntity<JsonNode> response = as(newAccount()).post(CUSTOMERS, customerBody(Map.of("birthDate",
                sixteenYearsAgo)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("birthDate").asString()).isEqualTo(sixteenYearsAgo);
    }

    @Test
    void theComplementIsOptional() {
        Map<String, Object> body = customerBody();
        ((Map<?, ?>) body.get("address")).remove("complement");

        ResponseEntity<JsonNode> response = as(newAccount()).post(CUSTOMERS, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().at("/address/complement").isNull()).isTrue();
    }

    @Test
    void anUnverifiedEmailIsAcceptedWhileTheCheckIsOff() {
        Account unverified = newAccount().withEmail(SyntheticData.email(), false);

        ResponseEntity<JsonNode> response = as(unverified).post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void anAccountWithoutEmailCannotRegister() {
        Account withoutEmail = newAccount().withEmail(null, null);

        ResponseEntity<JsonNode> response = as(withoutEmail).post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(customersOf(withoutEmail)).isZero();
    }

    @Test
    void theSameAccountCannotRegisterTwice() {
        Account account = newAccount();
        UUID id = register(account, customerBody());

        ResponseEntity<JsonNode> again = as(account).post(CUSTOMERS, customerBody());

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asString()).isEqualTo("CUSTOMER_ALREADY_REGISTERED");
        assertThat(again.getBody().get("message").asString()).startsWith("Esta conta já tem cadastro");
        assertThat(customersOf(account)).isEqualTo(1);
        assertThat(audited("CUSTOMER_REGISTERED", id)).isEqualTo(1);
    }

    @Test
    void aCpfRegisteredByAnotherAccountIsRefusedAndNothingIsSavedNorAudited() {
        Map<String, Object> body = customerBody();
        register(newAccount(), body);
        Account other = newAccount();
        int auditBefore = auditRows();

        ResponseEntity<JsonNode> response = as(other).post(CUSTOMERS, customerBody(Map.of("cpf", body.get("cpf"))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("CPF_ALREADY_REGISTERED");
        assertThat(response.getBody().toString()).doesNotContain((String) body.get("cpf"));
        assertThat(customersOf(other)).isZero();
        assertThat(auditRows()).isEqualTo(auditBefore);
    }

    @Test
    void repeatingWithTheSameKeyAnswersTheSameWithoutRegisteringAgainAndTheKeyKeepsOnlyTheId() {
        Account account = newAccount();
        String key = UUID.randomUUID().toString();
        ApiDriver customer = as(account).withIdempotencyKey(key);
        Map<String, Object> body = customerBody();

        ResponseEntity<JsonNode> first = customer.post(CUSTOMERS, body);
        ResponseEntity<JsonNode> second = customer.post(CUSTOMERS, body);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(customersOf(account)).isEqualTo(1);
        String id = first.getBody().get("id").asString();
        assertThat(audited("CUSTOMER_REGISTERED", UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.sql("SELECT response_body::text FROM idempotency_key WHERE actor_id = :actor AND key = :key")
                .param("actor", account.subject()).param("key", key).query(String.class).single())
                .isEqualTo("{\"id\": \"" + id + "\"}");
    }

    static Stream<Arguments> invalidFields() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return Stream.of(
                arguments("cpf", "12345678900", "CPF_INVALID"),
                arguments("cpf", "11111111111", "CPF_INVALID"),
                arguments("cpf", "529.982.247-2", "CPF_INVALID"),
                arguments("cpf", "5299822472a", "CPF_INVALID"),
                arguments("cpf", "", "REQUIRED"),
                arguments("birthDate", today.toString(), "DATE_INVALID"),
                arguments("birthDate", today.plusDays(1).toString(), "DATE_INVALID"),
                arguments("birthDate", "1899-12-31", "DATE_INVALID"),
                arguments("birthDate", "1990-13-40", "TYPE_INVALID"),
                arguments("birthDate", null, "REQUIRED"),
                arguments("phone", "11999990000", "FORMAT_INVALID"),
                arguments("phone", "+55 11 99999-0000", "FORMAT_INVALID"),
                arguments("phone", "+5501999990000", "FORMAT_INVALID"),
                arguments("fullName", " ", "REQUIRED"),
                arguments("fullName", "M".repeat(151), "SIZE_INVALID"),
                arguments("address", null, "REQUIRED"),
                arguments("address.zipCode", "01001-000", "FORMAT_INVALID"),
                arguments("address.state", "sp", "FORMAT_INVALID"),
                arguments("address.state", "XX", "FORMAT_INVALID"),
                arguments("address.street", "", "REQUIRED"),
                arguments("address.number", "1".repeat(11), "SIZE_INVALID"),
                arguments("address.complement", "c".repeat(61), "SIZE_INVALID"));
    }

    @ParameterizedTest(name = "{0} = {1} → {2}")
    @MethodSource("invalidFields")
    void anInvalidFieldIsRefusedWithItsCodeAndNothingIsSaved(String field, Object value, String code) {
        Account account = newAccount();
        Map<String, Object> changes = new HashMap<>();
        changes.put(field, value);

        ResponseEntity<JsonNode> response = as(account).post(CUSTOMERS, customerBody(changes));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldsOf(response)).contains(field + " " + code);
        assertThat(customersOf(account)).isZero();
    }

    @Test
    void theCpfAndTheBirthDateComeBackInOneAnswer() {
        Map<String, Object> body = customerBody(Map.of("cpf", "12345678900", "birthDate", "1899-01-01"));

        ResponseEntity<JsonNode> response = as(newAccount()).post(CUSTOMERS, body);

        assertThat(fieldsOf(response)).containsExactlyInAnyOrder("cpf CPF_INVALID", "birthDate DATE_INVALID");
    }

    @Test
    void theStaffDoesNotRegisterBuyers() {
        Map<String, Object> body = customerBody();

        ResponseEntity<JsonNode> seller = api().asSeller("vendedora").post(CUSTOMERS, body);
        ResponseEntity<JsonNode> admin = api().asAdmin("administrador").post(CUSTOMERS, body);

        assertThat(seller.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(admin.getBody().get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void aServiceTokenCannotRegister() {
        ResponseEntity<JsonNode> response = api().asService(TestTokens.SALES_CLIENT, "customers.read-payer")
                .post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void withoutATokenTheAnswerIsUnauthenticated() {
        ResponseEntity<JsonNode> response = api().post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void withoutTheIdempotencyKeyNothingIsCreated() {
        Account account = newAccount();

        ResponseEntity<JsonNode> response = as(account).withoutIdempotencyKey().post(CUSTOMERS, customerBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody().get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(customersOf(account)).isZero();
    }

    @RepeatedTest(5)
    void twoRegistrationsOfTheSameAccountAtOnceLeaveOneAndTheOtherHearsWhy() throws Exception {
        Account account = newAccount();

        List<ResponseEntity<JsonNode>> answers = atOnce(
                () -> as(account).post(CUSTOMERS, customerBody()),
                () -> as(account).post(CUSTOMERS, customerBody()));

        assertThat(statusesOf(answers)).containsExactlyInAnyOrder("201", "409 CUSTOMER_ALREADY_REGISTERED");
        assertThat(customersOf(account)).isEqualTo(1);
    }

    @RepeatedTest(5)
    void twoAccountsWithTheSameCpfAtOnceLeaveOne() throws Exception {
        String cpf = SyntheticData.cpf();

        List<ResponseEntity<JsonNode>> answers = atOnce(
                () -> as(newAccount()).post(CUSTOMERS, customerBody(Map.of("cpf", cpf))),
                () -> as(newAccount()).post(CUSTOMERS, customerBody(Map.of("cpf", cpf))));

        assertThat(statusesOf(answers)).containsExactlyInAnyOrder("201", "409 CPF_ALREADY_REGISTERED");
        assertThat(jdbc.sql("SELECT count(*) FROM customer WHERE cpf_index = :index")
                .param("index", indexer.index("cpf", cpf)).query(Integer.class).single()).isEqualTo(1);
    }

    /** Faz as chamadas em threads que largam juntas e devolve as respostas na ordem das chamadas. */
    @SafeVarargs
    private static List<ResponseEntity<JsonNode>> atOnce(Callable<ResponseEntity<JsonNode>>... calls) throws Exception {
        CyclicBarrier start = new CyclicBarrier(calls.length);
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (Callable<ResponseEntity<JsonNode>> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return call.call();
                }));
            }
            List<ResponseEntity<JsonNode>> answers = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> future : futures) {
                answers.add(future.get(30, TimeUnit.SECONDS));
            }
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<String> statusesOf(List<ResponseEntity<JsonNode>> answers) {
        return answers.stream()
                .map(answer -> answer.getStatusCode().value() == 201 ? "201"
                        : answer.getStatusCode().value() + " " + answer.getBody().get("code").asString())
                .toList();
    }
}
