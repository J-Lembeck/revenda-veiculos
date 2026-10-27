package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.crypto.FieldRef;
import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.test.TestTokens;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * As rotas que o {@code sales} chama com token de serviço: os dados da cobrança e do documento, completos e auditados
 * com a compra, e a conferência do CPF na retirada, pelo índice cego.
 */
@ExtendWith(OutputCaptureExtension.class)
class CustomerInternalTests extends CustomersApiTest {

    private static final String READ_PAYER = "customers.read-payer";
    private static final String PURCHASE_ID = "X-Purchase-Id";

    @Autowired
    private FieldCipher cipher;

    @Test
    void theSalesServiceReadsThePayerAsStoredWithTheReadAuditedAndNotCached(CapturedOutput output) {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");
        UUID id = register(account, body);
        UUID purchase = UUID.randomUUID();

        ResponseEntity<JsonNode> response = salesFor(purchase).get(payerPathOf(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        JsonNode payer = response.getBody();
        assertThat(payer.propertyNames()).containsExactlyInAnyOrder("customerId", "fullName", "cpf", "address");
        assertThat(payer.get("customerId").asString()).isEqualTo(id.toString());
        assertThat(payer.get("fullName").asString()).isEqualTo(rowOf(id).get("full_name"));
        assertThat(payer.get("cpf").asString()).isEqualTo(cpf).isEqualTo(stored(id, "cpf_enc"));
        assertThat(payer.get("address")).isEqualTo(JsonMapper.shared().readTree(stored(id, "address_enc")));
        assertThat(payer.get("address")).isEqualTo(JsonMapper.shared().valueToTree(body.get("address")));
        assertAudited("CUSTOMER_PAYER_DATA_READ", id, purchase, "Dados do pagador lidos para a compra " + purchase,
                cpf, account.email());
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), account.email(), "+5511999990000");
    }

    @Test
    void thePayerFollowsTheLastEdit() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> address = new HashMap<>(Map.of("zipCode", "04538133", "street", "Avenida dos Testes",
                "number", "2000", "district", "Itaim", "city", "São Paulo", "state", "SP"));
        as(account).patch(ME, Map.of("fullName", "Maria Exemplo Souza", "address", address));

        JsonNode payer = salesFor(UUID.randomUUID()).get(payerPathOf(id)).getBody();

        assertThat(payer.get("fullName").asString()).isEqualTo("Maria Exemplo Souza");
        assertThat(payer.at("/address/street").asString()).isEqualTo("Avenida dos Testes");
        assertThat(payer.at("/address/complement").isNull()).isTrue();
        assertThat(payer.get("address")).isEqualTo(JsonMapper.shared().readTree(stored(id, "address_enc")));
    }

    @Test
    void theSalesServiceReadsTheTransferDataWithTheEmailAuditedAndNotCached(CapturedOutput output) {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");
        UUID id = register(account, body);
        UUID purchase = UUID.randomUUID();

        ResponseEntity<JsonNode> response = salesFor(purchase).get(transferPathOf(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        JsonNode data = response.getBody();
        assertThat(data.propertyNames()).containsExactlyInAnyOrder("customerId", "fullName", "cpf", "email",
                "address");
        assertThat(data.get("customerId").asString()).isEqualTo(id.toString());
        assertThat(data.get("fullName").asString()).isEqualTo("Maria Exemplo da Silva");
        assertThat(data.get("cpf").asString()).isEqualTo(stored(id, "cpf_enc"));
        assertThat(data.get("email").asString()).isEqualTo(account.email()).isEqualTo(stored(id, "email_enc"));
        assertThat(data.get("address")).isEqualTo(JsonMapper.shared().readTree(stored(id, "address_enc")));
        assertAudited("CUSTOMER_TRANSFER_DATA_READ", id, purchase,
                "Dados do documento de transferência lidos para a compra " + purchase, cpf, account.email());
        assertThat(audited("CUSTOMER_PAYER_DATA_READ", id)).isZero();
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), account.email(), "+5511999990000");
    }

    @Test
    void everyReadIsAuditedOnItsOwn() {
        UUID id = register(newAccount(), customerBody());

        salesFor(UUID.randomUUID()).get(payerPathOf(id));
        salesFor(UUID.randomUUID()).get(payerPathOf(id));

        assertThat(audited("CUSTOMER_PAYER_DATA_READ", id)).isEqualTo(2);
    }

    @Test
    void theRightCpfMatchesWithOrWithoutPunctuationAndTheCheckIsAudited(CapturedOutput output) {
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");
        UUID id = register(newAccount(), body);
        UUID purchase = UUID.randomUUID();

        ResponseEntity<JsonNode> digits = salesFor(purchase).post(cpfCheckPathOf(id), Map.of("cpf", cpf));
        ResponseEntity<JsonNode> punctuated = salesFor(purchase)
                .post(cpfCheckPathOf(id), Map.of("cpf", SyntheticData.formatCpf(cpf)));

        assertThat(digits.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(digits.getBody().propertyNames()).containsExactly("matches");
        assertThat(digits.getBody().get("matches").asBoolean()).isTrue();
        assertThat(punctuated.getBody().get("matches").asBoolean()).isTrue();
        List<Map<String, Object>> audits = cpfChecksOf(id);
        assertThat(audits).hasSize(2).allSatisfy(audit -> assertThat(audit)
                .containsEntry("actor_type", "SERVICE").containsEntry("actor_id", TestTokens.SALES_CLIENT)
                .containsEntry("summary", "CPF conferido na retirada da compra " + purchase)
                .containsEntry("purchase", purchase.toString()).containsEntry("matches", "true"));
        assertThat(audits.toString()).doesNotContain(cpf, SyntheticData.formatCpf(cpf));
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf));
    }

    @Test
    void anotherValidCpfDoesNotMatchAndTheMismatchIsAudited(CapturedOutput output) {
        UUID id = register(newAccount(), customerBody());
        String otherCpf = SyntheticData.cpf();

        ResponseEntity<JsonNode> response = salesFor(UUID.randomUUID()).post(cpfCheckPathOf(id),
                Map.of("cpf", otherCpf));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("matches").asBoolean()).isFalse();
        assertThat(cpfChecksOf(id)).singleElement().satisfies(audit -> assertThat(audit)
                .containsEntry("matches", "false"));
        assertThat(cpfChecksOf(id).toString()).doesNotContain(otherCpf);
        assertThat(output).doesNotContain(otherCpf, SyntheticData.formatCpf(otherCpf));
    }

    static Stream<Arguments> invalidCpfs() {
        return Stream.of(
                arguments(null, "REQUIRED"),
                arguments("   ", "REQUIRED"),
                arguments("12345678900", "CPF_INVALID"),
                arguments("11111111111", "CPF_INVALID"),
                arguments("529.982.247-2", "CPF_INVALID"),
                arguments("5299822472a", "CPF_INVALID"));
    }

    @ParameterizedTest(name = "CPF {0} → {1}")
    @MethodSource("invalidCpfs")
    void aCpfThatIsNotACpfIsRefusedWithoutCheckingOrAuditing(String cpf, String code) {
        UUID id = register(newAccount(), customerBody());
        Map<String, Object> body = new HashMap<>();
        body.put("cpf", cpf);

        ResponseEntity<JsonNode> response = salesFor(UUID.randomUUID()).post(cpfCheckPathOf(id), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldsOf(response)).containsExactly("cpf " + code);
        assertThat(response.getBody().has("matches")).isFalse();
        assertThat(cpfChecksOf(id)).isEmpty();
    }

    static Stream<Arguments> routesById() {
        return Stream.of(
                arguments(HttpMethod.GET, "/payer", "CUSTOMER_PAYER_DATA_READ"),
                arguments(HttpMethod.GET, "/transfer-data", "CUSTOMER_TRANSFER_DATA_READ"),
                arguments(HttpMethod.POST, "/cpf-check", "CUSTOMER_CPF_CHECKED"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routesById")
    void aCustomerThatDoesNotExistIsNotFoundAndNotAudited(HttpMethod method, String route, String action) {
        UUID missing = UUID.randomUUID();

        ResponseEntity<JsonNode> response = call(salesFor(UUID.randomUUID()), method, pathOf(missing) + route);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(audited(action, missing)).isZero();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routesById")
    void theReadNeedsThePurchaseInTheHeaderAndNothingIsAuditedWithoutIt(HttpMethod method, String route,
            String action) {
        UUID id = register(newAccount(), customerBody());

        ResponseEntity<JsonNode> missing = call(sales(), method, pathOf(id) + route);
        ResponseEntity<JsonNode> blank = call(sales().withHeader(PURCHASE_ID, ""), method, pathOf(id) + route);
        ResponseEntity<JsonNode> notAUuid = call(sales().withHeader(PURCHASE_ID, "compra-1"), method,
                pathOf(id) + route);
        ResponseEntity<JsonNode> badId = call(salesFor(UUID.randomUUID()), method,
                "/internal/customers/nao-e-um-uuid" + route);

        for (ResponseEntity<JsonNode> refused : List.of(missing, blank, notAUuid, badId)) {
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refused.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        }
        assertThat(fieldsOf(missing)).containsExactly(PURCHASE_ID + " REQUIRED");
        assertThat(fieldsOf(blank)).containsExactly(PURCHASE_ID + " REQUIRED");
        assertThat(fieldsOf(notAUuid)).containsExactly(PURCHASE_ID + " TYPE_INVALID");
        assertThat(fieldsOf(badId)).containsExactly("id TYPE_INVALID");
        assertThat(audited(action, id)).isZero();
    }

    static Stream<Arguments> internalRoutes() {
        return Stream.concat(Stream.of(arguments(HttpMethod.GET, "/eligibility", "")), routesById());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("internalRoutes")
    void onlyAServiceWithTheScopeCallsTheInternalRoutes(HttpMethod method, String route, String action) {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        String path = route.equals("/eligibility")
                ? "/internal/customers/by-subject/" + account.subject() + route
                : pathOf(id) + route;
        String purchase = UUID.randomUUID().toString();
        List<ApiDriver> people = List.of(as(account), api().asSeller("vendedora"), api().asAdmin("administrador"),
                api().as(TestTokens.token().subject("administrador").roles("ADMIN").scopes(READ_PAYER).encode()));
        int auditBefore = auditRows();

        for (ApiDriver person : people) {
            ResponseEntity<JsonNode> response = call(person.withHeader(PURCHASE_ID, purchase), method, path);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().get("code").asString()).isEqualTo("FORBIDDEN");
        }
        ResponseEntity<JsonNode> otherScope = call(api().asService(TestTokens.SALES_CLIENT, "vehicles.reserve")
                .withHeader(PURCHASE_ID, purchase), method, path);
        ResponseEntity<JsonNode> otherService = call(api().asService(TestTokens.CUSTOMERS_CLIENT, READ_PAYER)
                .withHeader(PURCHASE_ID, purchase), method, path);
        ResponseEntity<JsonNode> anonymous = call(api().withHeader(PURCHASE_ID, purchase), method, path);

        assertThat(otherScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(otherService.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(auditRows()).isEqualTo(auditBefore);
    }

    @Test
    void anUndeclaredInternalRouteIsForbiddenEvenWithTheScope() {
        UUID id = register(newAccount(), customerBody());

        ResponseEntity<JsonNode> response = salesFor(UUID.randomUUID()).get(pathOf(id) + "/cpf-reveal");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void assertAudited(String action, UUID id, UUID purchase, String summary, String cpf, String email) {
        Map<String, Object> audit = jdbc.sql("""
                        SELECT actor_type, actor_id, entity_type, summary, reason, data::text AS data,
                               data -> 'extra' ->> 'purchaseId' AS purchase
                          FROM audit_log WHERE action = :action AND entity_id = :id""")
                .param("action", action)
                .param("id", id)
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "SERVICE").containsEntry("actor_id", TestTokens.SALES_CLIENT)
                .containsEntry("entity_type", "CUSTOMER").containsEntry("summary", summary)
                .containsEntry("reason", null).containsEntry("purchase", purchase.toString());
        assertThat(JsonMapper.shared().readTree((String) audit.get("data")).propertyNames()).containsExactly("extra");
        assertThat(audit.toString()).doesNotContain(cpf, SyntheticData.formatCpf(cpf), email, "+5511999990000",
                "Maria Exemplo", "Praça da Sé", "01001000", "1990-05-17");
    }

    private List<Map<String, Object>> cpfChecksOf(UUID id) {
        return jdbc.sql("""
                        SELECT actor_type, actor_id, summary, data::text AS data,
                               data -> 'extra' ->> 'purchaseId' AS purchase, data -> 'extra' ->> 'matches' AS matches
                          FROM audit_log WHERE action = 'CUSTOMER_CPF_CHECKED' AND entity_id = :id""")
                .param("id", id)
                .query()
                .listOfRows();
    }

    private String stored(UUID id, String column) {
        return cipher.decrypt(new FieldRef("customer", column, id), (byte[]) rowOf(id).get(column));
    }

    private static ResponseEntity<JsonNode> call(ApiDriver driver, HttpMethod method, String path) {
        return HttpMethod.POST.equals(method)
                ? driver.post(path, Map.of("cpf", SyntheticData.cpf()))
                : driver.get(path);
    }

    private ApiDriver sales() {
        return api().asService(TestTokens.SALES_CLIENT, READ_PAYER);
    }

    private ApiDriver salesFor(UUID purchase) {
        return sales().withHeader(PURCHASE_ID, purchase.toString());
    }

    private static String pathOf(UUID id) {
        return "/internal/customers/" + id;
    }

    private static String payerPathOf(UUID id) {
        return pathOf(id) + "/payer";
    }

    private static String transferPathOf(UUID id) {
        return pathOf(id) + "/transfer-data";
    }

    private static String cpfCheckPathOf(UUID id) {
        return pathOf(id) + "/cpf-check";
    }
}
