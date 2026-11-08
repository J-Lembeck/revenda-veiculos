package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** UC-15, acesso e portabilidade: o titular leva o cadastro e o histórico de quem acessou o dado. */
class CustomerExportTests extends CustomersApiTest {

    private static final String EXPORT = ME + "/export";

    @Test
    void theOwnerGetsTheWholeProfileWithTheCpfMasked() {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        UUID id = register(account, body);

        ResponseEntity<JsonNode> answer = as(account).get(EXPORT);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode customer = answer.getBody().get("customer");
        assertThat(customer.get("id").asString()).isEqualTo(id.toString());
        assertThat(customer.get("fullName").asString()).isEqualTo("Maria Exemplo da Silva");
        assertThat(customer.get("email").asString()).isEqualTo(account.email());
        assertThat(customer.get("phone").asString()).isEqualTo("+5511999990000");
        assertThat(customer.get("birthDate").asString()).isEqualTo("1990-05-17");
        assertThat(customer.get("address").get("street").asString()).isEqualTo("Praça da Sé");
        assertThat(customer.get("cpfMasked").asString()).isEqualTo(maskedCpf((String) body.get("cpf")));
        assertThat(customer.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(customer.get("deletionRequestedAt").isNull()).isTrue();
        assertThat(answer.getBody().toString()).doesNotContain((String) body.get("cpf"));
        assertThat(answer.getBody().get("elsewhere").get(0).asString()).contains("GET /purchases");
    }

    @Test
    void theHistoryShowsWhoReadTheDataWithoutTheirIdentity() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        UUID purchase = UUID.randomUUID();
        String admin = "admin-" + UUID.randomUUID();
        api().asService(TestTokens.SALES_CLIENT, "customers.read-payer").withHeader("X-Purchase-Id", purchase.toString())
                .get("/internal/customers/" + id + "/payer");
        api().asAdmin(admin).post(CUSTOMERS + "/" + id + "/cpf-reveal",
                Map.of("reason", "Conferência do contrato de venda"));

        JsonNode history = as(account).get(EXPORT).getBody().get("history");

        assertThat(actions(history)).containsExactly("CUSTOMER_REGISTERED", "CUSTOMER_PAYER_DATA_READ",
                "CUSTOMER_CPF_REVEALED");
        assertThat(history.get(0).get("actor").asString()).isEqualTo("OWNER");
        assertThat(history.get(1).get("actor").asString()).isEqualTo("SERVICE");
        assertThat(history.get(1).get("service").asString()).isEqualTo(TestTokens.SALES_CLIENT);
        assertThat(history.get(1).get("purchaseId").asString()).isEqualTo(purchase.toString());
        assertThat(history.get(2).get("actor").asString()).isEqualTo("STAFF");
        assertThat(history.get(2).get("role").asString()).isEqualTo("ADMIN");
        assertThat(history.get(2).get("reason").asString()).isEqualTo("Conferência do contrato de venda");
        assertThat(history.toString()).doesNotContain(admin, account.subject());
    }

    @Test
    void eachExportIsAuditedAndShowsUpInTheNextOne() {
        Account account = newAccount();
        UUID id = register(account, customerBody());

        as(account).get(EXPORT);
        JsonNode second = as(account).get(EXPORT).getBody();

        assertThat(audited("CUSTOMER_EXPORTED", id)).isEqualTo(2);
        assertThat(actions(second.get("history"))).containsExactly("CUSTOMER_REGISTERED", "CUSTOMER_EXPORTED");
    }

    @Test
    void anAccountWithoutProfileGetsNotFoundAndNothingIsAudited() {
        int before = auditRows();

        ResponseEntity<JsonNode> answer = as(newAccount()).get(EXPORT);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(answer.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(auditRows()).isEqualTo(before);
    }

    private static List<String> actions(JsonNode history) {
        return history.valueStream().map(entry -> entry.get("action").asString()).toList();
    }
}
