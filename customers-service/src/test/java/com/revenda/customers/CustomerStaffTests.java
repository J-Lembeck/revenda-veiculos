package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.test.TestTokens;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** UC-14: a equipe vê o cliente mascarado e só o administrador revela o CPF, com motivo e auditado. */
@ExtendWith(OutputCaptureExtension.class)
class CustomerStaffTests extends CustomersApiTest {

    private static final String SELLER = "vendedora";
    private static final String ADMIN = "administrador";
    private static final String REASON = "Conferência do documento do comprador na retirada";

    @Autowired
    private MeterRegistry meters;

    @Test
    void theSellerSeesTheMinimumWithCpfEmailAndPhoneMaskedAndTheReadIsNotAudited(CapturedOutput output) {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");
        UUID id = register(account, body);
        int auditBefore = auditRows();

        ResponseEntity<JsonNode> response = api().asSeller(SELLER).get(pathOf(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode customer = response.getBody();
        assertThat(customer.propertyNames()).containsExactlyInAnyOrder("id", "fullName", "cpfMasked", "emailMasked",
                "phoneMasked", "city", "state", "status", "createdAt", "updatedAt");
        assertThat(customer.get("id").asString()).isEqualTo(id.toString());
        assertThat(customer.get("fullName").asString()).isEqualTo("Maria Exemplo da Silva");
        assertThat(customer.get("cpfMasked").asString()).isEqualTo(maskedCpf(cpf));
        assertThat(customer.get("emailMasked").asString()).isEqualTo("p***@example.test");
        assertThat(customer.get("phoneMasked").asString()).isEqualTo("(11) *****-0000");
        assertThat(customer.get("city").asString()).isEqualTo("São Paulo");
        assertThat(customer.get("state").asString()).isEqualTo("SP");
        assertThat(customer.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(customer.toString()).doesNotContain(cpf, account.email(), "+5511999990000", "1990-05-17",
                "Praça da Sé", "01001000");
        assertThat(auditRows()).isEqualTo(auditBefore);
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), account.email(), "+5511999990000");
    }

    @Test
    void theAdminSeesTheCustomerToo() {
        UUID id = register(newAccount(), customerBody());

        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).get(pathOf(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id").asString()).isEqualTo(id.toString());
    }

    @Test
    void aCustomerThatDoesNotExistIsNotFoundAndAnIdThatIsNotAUuidIsInvalid() {
        ResponseEntity<JsonNode> missing = api().asSeller(SELLER).get(pathOf(UUID.randomUUID()));
        ResponseEntity<JsonNode> invalid = api().asSeller(SELLER).get(CUSTOMERS + "/nao-e-um-uuid");

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(invalid)).containsExactly("id TYPE_INVALID");
    }

    @Test
    void customersAndServicesDoNotUseTheStaffView() {
        Account account = newAccount();
        UUID id = register(account, customerBody());

        ResponseEntity<JsonNode> own = as(account).get(pathOf(id));
        ResponseEntity<JsonNode> service = api().asService(TestTokens.SALES_CLIENT, "customers.read-payer")
                .get(pathOf(id));
        ResponseEntity<JsonNode> anonymous = api().get(pathOf(id));

        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(own.getBody().get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(service.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theStaffDoesNotReachTheBuyerRouteThroughTheIdPattern() {
        ResponseEntity<JsonNode> response = api().asSeller(SELLER).get(ME);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theAdminRevealsTheCpfWithTheReasonAuditedCountedAndNotCached(CapturedOutput output) {
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");
        UUID id = register(newAccount(), body);
        double revealsBefore = reveals();

        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).post(revealPathOf(id), Map.of("reason", REASON));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("cpf").asString()).isEqualTo(cpf);
        assertThat(response.getBody().propertyNames()).containsExactly("cpf");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        Map<String, Object> audit = jdbc.sql("""
                        SELECT actor_type, actor_id, actor_role, entity_type, summary, reason, data
                          FROM audit_log WHERE action = 'CUSTOMER_CPF_REVEALED' AND entity_id = :id""")
                .param("id", id)
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_id", ADMIN)
                .containsEntry("actor_role", "ADMIN").containsEntry("entity_type", "CUSTOMER")
                .containsEntry("summary", "CPF completo revelado").containsEntry("reason", REASON)
                .containsEntry("data", null);
        assertThat(reveals()).isEqualTo(revealsBefore + 1);
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf));
    }

    @Test
    void everyRevealIsAuditedOnItsOwn() {
        UUID id = register(newAccount(), customerBody());
        ApiDriver admin = api().asAdmin(ADMIN);

        admin.post(revealPathOf(id), Map.of("reason", REASON));
        admin.post(revealPathOf(id), Map.of("reason", "Pedido do titular por telefone, protocolo 42"));

        assertThat(audited("CUSTOMER_CPF_REVEALED", id)).isEqualTo(2);
    }

    @Test
    void aCpfWrittenInTheReasonIsMaskedInTheAudit() {
        UUID id = register(newAccount(), customerBody());
        String otherCpf = SyntheticData.cpf();

        api().asAdmin(ADMIN).post(revealPathOf(id), Map.of("reason", "Confere com " + otherCpf + " do documento"));

        String reason = jdbc.sql("SELECT reason FROM audit_log WHERE action = 'CUSTOMER_CPF_REVEALED' AND entity_id = :id")
                .param("id", id).query(String.class).single();
        assertThat(reason).startsWith("Confere com ").doesNotContain(otherCpf);
    }

    static Stream<Arguments> invalidReasons() {
        return Stream.of(
                arguments(null, "REQUIRED"),
                arguments("          ", "REQUIRED"),
                arguments("123456789", "SIZE_INVALID"),
                arguments("m".repeat(301), "SIZE_INVALID"));
    }

    @ParameterizedTest(name = "motivo {0} → {1}")
    @MethodSource("invalidReasons")
    void aRevealWithoutAProperReasonRevealsNothingAndAuditsNothing(String reason, String code) {
        UUID id = register(newAccount(), customerBody());
        double revealsBefore = reveals();
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);

        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).post(revealPathOf(id), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).contains("reason " + code);
        assertThat(response.getBody().has("cpf")).isFalse();
        assertThat(audited("CUSTOMER_CPF_REVEALED", id)).isZero();
        assertThat(reveals()).isEqualTo(revealsBefore);
    }

    @Test
    void aReasonOfTenCharactersIsEnough() {
        UUID id = register(newAccount(), customerBody());

        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).post(revealPathOf(id), Map.of("reason", "1234567890"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void revealingACustomerThatDoesNotExistIsNotFoundAndNotAudited() {
        UUID missing = UUID.randomUUID();

        ResponseEntity<JsonNode> response = api().asAdmin(ADMIN).post(revealPathOf(missing), Map.of("reason", REASON));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(audited("CUSTOMER_CPF_REVEALED", missing)).isZero();
    }

    @Test
    void onlyTheAdminReveals() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        List<ApiDriver> others = List.of(api().asSeller(SELLER), as(account),
                api().asService(TestTokens.SALES_CLIENT, "customers.read-payer"));

        for (ApiDriver other : others) {
            ResponseEntity<JsonNode> response = other.post(revealPathOf(id), Map.of("reason", REASON));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().has("cpf")).isFalse();
        }
        assertThat(api().post(revealPathOf(id), Map.of("reason", REASON)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(audited("CUSTOMER_CPF_REVEALED", id)).isZero();
    }

    private double reveals() {
        return meters.counter("customers.cpf.reveals").count();
    }

    private static String pathOf(UUID id) {
        return CUSTOMERS + "/" + id;
    }

    private static String revealPathOf(UUID id) {
        return pathOf(id) + "/cpf-reveal";
    }
}
