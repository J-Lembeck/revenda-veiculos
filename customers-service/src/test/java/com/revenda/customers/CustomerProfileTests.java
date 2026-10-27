package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.crypto.FieldRef;
import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.test.TestTokens;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** UC-07: o comprador vê e edita o próprio cadastro (RN-07). */
@ExtendWith(OutputCaptureExtension.class)
class CustomerProfileTests extends CustomersApiTest {

    private static final String NEW_PHONE = "+5511988887777";
    private static final Map<String, Object> NEW_ADDRESS = Map.of("zipCode", "04538133", "street", "Avenida dos Testes",
            "number", "2000", "complement", "sala 5", "district", "Itaim", "city", "São Paulo", "state", "SP");
    private static final List<String> CIPHERED = List.of("cpf_enc", "email_enc", "phone_enc", "birth_date_enc",
            "address_enc");

    @Autowired
    private FieldCipher cipher;

    @Test
    void theCustomerSeesTheOwnRecordMaskedAsInTheRegistrationAndTheReadIsNotAudited(CapturedOutput output) {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");
        ResponseEntity<JsonNode> registered = as(account).post(CUSTOMERS, body);
        int auditBefore = auditRows();

        ResponseEntity<JsonNode> response = as(account).get(ME);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode customer = response.getBody();
        assertThat(customer).isEqualTo(registered.getBody());
        assertThat(customer.get("cpfMasked").asString()).isEqualTo(maskedCpf(cpf));
        assertThat(customer.get("emailMasked").asString()).isEqualTo("p***@example.test");
        assertThat(customer.get("phoneMasked").asString()).isEqualTo("(11) *****-0000");
        assertThat(customer.get("birthDate").asString()).isEqualTo("1990-05-17");
        assertThat(customer.at("/address/street").asString()).isEqualTo("Praça da Sé");
        assertThat(customer.propertyNames()).doesNotContain("cpf", "email", "phone", "identitySubject");
        assertThat(customer.toString()).doesNotContain(cpf, account.email(), "+5511999990000");
        assertThat(auditRows()).isEqualTo(auditBefore);
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), account.email(), "+5511999990000");
    }

    @Test
    void eachAccountSeesOnlyItsOwnRecord() {
        Account ana = newAccount();
        Account duda = newAccount();
        UUID anaId = register(ana, customerBody());
        Map<String, Object> dudaBody = customerBody(Map.of("fullName", "Duda Exemplo"));
        UUID dudaId = register(duda, dudaBody);

        JsonNode anaView = as(ana).get(ME).getBody();
        JsonNode dudaView = as(duda).get(ME).getBody();

        assertThat(anaView.get("id").asString()).isEqualTo(anaId.toString());
        assertThat(anaView.get("fullName").asString()).isEqualTo("Maria Exemplo da Silva");
        assertThat(dudaView.get("id").asString()).isEqualTo(dudaId.toString());
        assertThat(dudaView.get("fullName").asString()).isEqualTo("Duda Exemplo");
        assertThat(dudaView.get("cpfMasked").asString()).isEqualTo(maskedCpf((String) dudaBody.get("cpf")));
    }

    @Test
    void anAccountWithoutARecordFindsNothingToSeeOrEdit() {
        register(newAccount(), customerBody());
        Account withoutRecord = newAccount();

        ResponseEntity<JsonNode> view = as(withoutRecord).get(ME);
        ResponseEntity<JsonNode> edit = as(withoutRecord).patch(ME, Map.of("phone", NEW_PHONE));

        assertThat(view.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(view.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(edit.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(customersOf(withoutRecord)).isZero();
    }

    static Stream<Arguments> singleChanges() {
        return Stream.of(
                arguments("fullName", "Maria Exemplo Souza", "full_name", "FULL_NAME"),
                arguments("phone", NEW_PHONE, "phone_enc", "PHONE"),
                arguments("address", NEW_ADDRESS, "address_enc", "ADDRESS"));
    }

    @ParameterizedTest(name = "{0} → só {2} muda")
    @MethodSource("singleChanges")
    void eachFieldChangesAloneAndOnlyItsColumnIsWrittenAgain(String field, Object value, String column,
            String changedField) {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);

        ResponseEntity<JsonNode> response = as(account).patch(ME, Map.of(field, value));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> after = rowOf(id);
        assertThat(changedColumns(before, after)).containsExactlyInAnyOrder(column, "version", "updated_at");
        assertThat(after).containsEntry("version", 1).containsEntry("created_at", before.get("created_at"));
        assertThat(changedFieldsAudited(id)).isEqualTo("[\"" + changedField + "\"]");
        assertThat(as(account).get(ME).getBody()).isEqualTo(response.getBody());
    }

    @Test
    void theNamePhoneAndAddressChangeTogetherStayCipheredAndTheCpfAndBirthDateStay(CapturedOutput output) {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        String cpf = (String) body.get("cpf");
        UUID id = register(account, body);

        ResponseEntity<JsonNode> response = as(account).patch(ME,
                Map.of("fullName", "  Maria Exemplo Souza ", "phone", NEW_PHONE, "address", NEW_ADDRESS));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode customer = response.getBody();
        assertThat(customer.get("fullName").asString()).isEqualTo("Maria Exemplo Souza");
        assertThat(customer.get("phoneMasked").asString()).isEqualTo("(11) *****-7777");
        assertThat(customer.get("address")).isEqualTo(as(account).get(ME).getBody().get("address"));
        assertThat(customer.at("/address/street").asString()).isEqualTo("Avenida dos Testes");
        assertThat(customer.at("/address/zipCode").asString()).isEqualTo("04538133");
        assertThat(customer.at("/address/complement").asString()).isEqualTo("sala 5");
        assertThat(customer.get("cpfMasked").asString()).isEqualTo(maskedCpf(cpf));
        assertThat(customer.get("emailMasked").asString()).isEqualTo("p***@example.test");
        assertThat(customer.get("birthDate").asString()).isEqualTo("1990-05-17");
        assertThat(customer.toString()).doesNotContain(cpf, account.email(), NEW_PHONE);
        Map<String, Object> row = rowOf(id);
        assertThat(row).containsEntry("full_name", "Maria Exemplo Souza");
        for (String column : CIPHERED) {
            assertThat(new String((byte[]) row.get(column), StandardCharsets.ISO_8859_1)).as(column)
                    .doesNotContain(cpf, account.email(), "988887777", "Avenida", "04538133", "1990-05-17");
        }
        assertThat(cipher.decrypt(new FieldRef("customer", "phone_enc", id), (byte[]) row.get("phone_enc")))
                .isEqualTo(NEW_PHONE);
        assertThatThrownBy(() -> cipher.decrypt(new FieldRef("customer", "phone_enc", UUID.randomUUID()),
                (byte[]) row.get("phone_enc"))).isInstanceOf(IllegalStateException.class);
        assertThat(changedFieldsAudited(id)).isEqualTo("[\"FULL_NAME\", \"PHONE\", \"ADDRESS\"]");
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), account.email(), NEW_PHONE, "988887777");
    }

    @Test
    void theAddressComesWholeAndAComplementThatDoesNotComeIsCleared() {
        Account account = newAccount();
        register(account, customerBody());
        Map<String, Object> address = new HashMap<>(NEW_ADDRESS);
        address.remove("complement");

        ResponseEntity<JsonNode> response = as(account).patch(ME, Map.of("address", address));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().at("/address/complement").isNull()).isTrue();
        assertThat(response.getBody().at("/address/street").asString()).isEqualTo("Avenida dos Testes");
    }

    @Test
    void aPartialAddressIsRefusedWithEveryMissingField() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);

        ResponseEntity<JsonNode> response = as(account).patch(ME, Map.of("address", Map.of("zipCode", "04538133")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldsOf(response)).containsExactlyInAnyOrder("address.street REQUIRED", "address.number REQUIRED",
                "address.district REQUIRED", "address.city REQUIRED", "address.state REQUIRED");
        assertThat(changedColumns(before, rowOf(id))).isEmpty();
    }

    @Test
    void aFieldThatIsMissingOrNullStaysAsItIs() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);
        Map<String, Object> changes = new HashMap<>();
        changes.put("phone", NEW_PHONE);
        changes.put("fullName", null);
        changes.put("address", null);

        ResponseEntity<JsonNode> response = as(account).patch(ME, changes);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("fullName").asString()).isEqualTo("Maria Exemplo da Silva");
        assertThat(response.getBody().at("/address/street").asString()).isEqualTo("Praça da Sé");
        assertThat(changedColumns(before, rowOf(id))).containsExactlyInAnyOrder("phone_enc", "version", "updated_at");
    }

    static Stream<Arguments> invalidChanges() {
        return Stream.of(
                arguments("fullName", " ", "REQUIRED"),
                arguments("fullName", "M".repeat(151), "SIZE_INVALID"),
                arguments("phone", "", "REQUIRED"),
                arguments("phone", "11988887777", "FORMAT_INVALID"),
                arguments("phone", "+55 11 98888-7777", "FORMAT_INVALID"),
                arguments("phone", "+5501988887777", "FORMAT_INVALID"),
                arguments("address", "Avenida dos Testes, 2000", "TYPE_INVALID"),
                arguments("address.zipCode", "04538-133", "FORMAT_INVALID"),
                arguments("address.state", "sp", "FORMAT_INVALID"),
                arguments("address.street", "", "REQUIRED"),
                arguments("address.number", "1".repeat(11), "SIZE_INVALID"),
                arguments("address.complement", "c".repeat(61), "SIZE_INVALID"),
                arguments("cpf", SyntheticData.cpf(), "NOT_EDITABLE"),
                arguments("birthDate", "1991-01-01", "NOT_EDITABLE"),
                arguments("email", "outra.pessoa@example.test", "NOT_EDITABLE"));
    }

    @ParameterizedTest(name = "{0} = {1} → {2}")
    @MethodSource("invalidChanges")
    void anInvalidChangeIsRefusedWithItsCodeAndNothingChanges(String field, Object value, String code) {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);
        Map<String, Object> changes = new HashMap<>();
        changes.put("phone", NEW_PHONE);
        if (field.startsWith("address.")) {
            Map<String, Object> address = new HashMap<>(NEW_ADDRESS);
            address.put(field.substring("address.".length()), value);
            changes.put("address", address);
        } else {
            changes.put(field, value);
        }

        ResponseEntity<JsonNode> response = as(account).patch(ME, changes);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldsOf(response)).contains(field + " " + code);
        assertThat(changedColumns(before, rowOf(id))).isEmpty();
        assertThat(audited("CUSTOMER_UPDATED", id)).isZero();
    }

    @Test
    void theCpfBirthDateAndEmailComeBackTogetherWithTheMessageOfEach() {
        Account account = newAccount();
        register(account, customerBody());
        String otherCpf = SyntheticData.cpf();

        ResponseEntity<JsonNode> response = as(account).patch(ME,
                Map.of("cpf", otherCpf, "birthDate", "1991-01-01", "email", "outra.pessoa@example.test"));

        assertThat(fieldsOf(response)).containsExactlyInAnyOrder("cpf NOT_EDITABLE", "birthDate NOT_EDITABLE",
                "email NOT_EDITABLE");
        assertThat(response.getBody().get("fields").toString())
                .contains("O CPF não muda pelo autoatendimento", "A data de nascimento não muda",
                        "O e-mail é o da sua conta")
                .doesNotContain(otherCpf, "outra.pessoa@example.test");
    }

    @Test
    void anEditWithoutChangesAnswersTheRecordWithoutWritingAnything() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);
        JsonNode current = as(account).get(ME).getBody();

        ResponseEntity<JsonNode> empty = as(account).patch(ME, Map.of());
        ResponseEntity<JsonNode> sameValues = as(account).patch(ME, Map.of("fullName", "Maria Exemplo da Silva",
                "phone", "+5511999990000", "address", customerBody().get("address")));

        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).isEqualTo(current);
        assertThat(sameValues.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sameValues.getBody()).isEqualTo(current);
        assertThat(changedColumns(before, rowOf(id))).isEmpty();
        assertThat(audited("CUSTOMER_UPDATED", id)).isZero();
    }

    @Test
    void theEditIsAuditedWithTheVersionsAndTheChangedFieldsButNoPersonalData() {
        Account account = newAccount();
        Map<String, Object> body = customerBody();
        UUID id = register(account, body);

        as(account).patch(ME, Map.of("phone", NEW_PHONE, "address", NEW_ADDRESS));

        Map<String, Object> audit = jdbc.sql("""
                        SELECT actor_type, actor_id, actor_role, entity_type, summary, data::text AS data
                          FROM audit_log WHERE action = 'CUSTOMER_UPDATED' AND entity_id = :id""")
                .param("id", id)
                .query()
                .singleRow();
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_id", account.subject())
                .containsEntry("actor_role", "CUSTOMER").containsEntry("entity_type", "CUSTOMER")
                .containsEntry("summary", "Cadastro atualizado pelo titular");
        assertThat((String) audit.get("data")).isEqualTo("{\"after\": {\"status\": \"ACTIVE\", \"version\": 1}, "
                        + "\"extra\": {\"changedFields\": [\"PHONE\", \"ADDRESS\"]}, "
                        + "\"before\": {\"status\": \"ACTIVE\", \"version\": 0}}")
                .doesNotContain((String) body.get("cpf"), account.email(), "988887777", "Avenida", "Maria");
    }

    @Test
    void theEmailFollowsTheAccountOnTheNextEditAndIsAudited() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);
        Account renamed = account.withEmail("nova." + account.email(), true);

        ResponseEntity<JsonNode> response = as(renamed).patch(ME, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("emailMasked").asString()).isEqualTo("n***@example.test");
        Map<String, Object> after = rowOf(id);
        assertThat(changedColumns(before, after)).containsExactlyInAnyOrder("email_enc", "version", "updated_at");
        assertThat(cipher.decrypt(new FieldRef("customer", "email_enc", id), (byte[]) after.get("email_enc")))
                .isEqualTo(renamed.email());
        assertThat(changedFieldsAudited(id)).isEqualTo("[\"EMAIL\"]");
        assertThat(as(account).get(ME).getBody().get("emailMasked").asString()).isEqualTo("n***@example.test");
    }

    @Test
    void aTokenWithoutEmailKeepsTheStoredOne() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);

        ResponseEntity<JsonNode> response = as(account.withEmail(null, null)).patch(ME, Map.of("phone", NEW_PHONE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("emailMasked").asString()).isEqualTo("p***@example.test");
        assertThat(changedColumns(before, rowOf(id))).containsExactlyInAnyOrder("phone_enc", "version", "updated_at");
    }

    @RepeatedTest(5)
    void twoEditsOfDifferentFieldsAtOnceAreBothKept() throws Exception {
        Account account = newAccount();
        UUID id = register(account, customerBody());

        List<ResponseEntity<JsonNode>> answers = atOnce(
                () -> as(account).patch(ME, Map.of("phone", NEW_PHONE)),
                () -> as(account).patch(ME, Map.of("address", NEW_ADDRESS)));

        assertThat(answers).allSatisfy(answer -> assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK));
        JsonNode customer = as(account).get(ME).getBody();
        assertThat(customer.get("phoneMasked").asString()).isEqualTo("(11) *****-7777");
        assertThat(customer.at("/address/street").asString()).isEqualTo("Avenida dos Testes");
        assertThat(rowOf(id)).containsEntry("version", 2);
        assertThat(audited("CUSTOMER_UPDATED", id)).isEqualTo(2);
    }

    @RepeatedTest(5)
    void twoEditsOfTheSamePhoneAtOnceLeaveOneOfThemAndBothAudited() throws Exception {
        Account account = newAccount();
        UUID id = register(account, customerBody());

        List<ResponseEntity<JsonNode>> answers = atOnce(
                () -> as(account).patch(ME, Map.of("phone", "+5511988881111")),
                () -> as(account).patch(ME, Map.of("phone", "+5511988882222")));

        assertThat(answers).allSatisfy(answer -> assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(as(account).get(ME).getBody().get("phoneMasked").asString())
                .isIn("(11) *****-1111", "(11) *****-2222");
        assertThat(rowOf(id)).containsEntry("version", 2);
        assertThat(audited("CUSTOMER_UPDATED", id)).isEqualTo(2);
    }

    @Test
    void theStaffAndServicesNeitherSeeNorEditThroughTheBuyerRoutes() {
        Account account = newAccount();
        register(account, customerBody());
        List<ApiDriver> others = List.of(api().asSeller("vendedora"), api().asAdmin("administrador"),
                api().asService(TestTokens.SALES_CLIENT, "customers.read-payer"));

        for (ApiDriver other : others) {
            ResponseEntity<JsonNode> view = other.get(ME);
            ResponseEntity<JsonNode> edit = other.patch(ME, Map.of("phone", NEW_PHONE));
            assertThat(view.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(view.getBody().get("code").asString()).isEqualTo("FORBIDDEN");
            assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void withoutATokenBothRoutesAreUnauthenticated() {
        ResponseEntity<JsonNode> view = api().get(ME);
        ResponseEntity<JsonNode> edit = api().patch(ME, Map.of("phone", NEW_PHONE));

        assertThat(view.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(view.getBody().get("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String changedFieldsAudited(UUID id) {
        return jdbc.sql("""
                        SELECT data #>> '{extra,changedFields}' FROM audit_log
                         WHERE action = 'CUSTOMER_UPDATED' AND entity_id = :id""")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
