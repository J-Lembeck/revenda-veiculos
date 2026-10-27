package com.revenda.customers;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.test.TestTokenConfiguration;
import com.revenda.common.test.TestTokens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * O serviço de pé, com o banco e o KMS de teste e tokens de teste, chamado pelo HTTP. O limite de taxa fica desligado:
 * os testes cadastram muito mais que 5 por minuto da mesma origem; o teste do limite o liga.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestTokenConfiguration.class, CustomersTestKms.class})
@TestPropertySource(properties = {
        "revenda.security.oidc.issuer-uri=" + TestTokens.ISSUER,
        "revenda.security.rate-limit.enabled=false"})
abstract class CustomersApiTest {

    protected static final String CUSTOMERS = "/customers";
    protected static final String ME = "/customers/me";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    protected JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "customers");
    }

    protected ApiDriver api() {
        return new ApiDriver(rest);
    }

    /** Uma conta nova no provedor de identidade, com papel {@code CUSTOMER} e o e-mail verificado. */
    protected static Account newAccount() {
        return new Account(UUID.randomUUID().toString(), SyntheticData.email(), true);
    }

    /** O condutor com o token da conta: o {@code sub}, o e-mail e o {@code email_verified} dela, se houver. */
    protected ApiDriver as(Account account) {
        return api().as(TestTokens.token()
                .subject(account.subject())
                .roles("CUSTOMER")
                .claim("email", account.email())
                .claim("email_verified", account.emailVerified())
                .encode());
    }

    /**
     * Um cadastro válido com CPF sintético inédito, como o corpo do {@code POST /customers}; o que vier em
     * {@code changes} troca, e {@code address.<campo>} troca o campo do endereço.
     */
    protected static Map<String, Object> customerBody(Map<String, Object> changes) {
        Map<String, Object> address = new HashMap<>();
        address.put("zipCode", "01001000");
        address.put("street", "Praça da Sé");
        address.put("number", "100");
        address.put("complement", "apto 1");
        address.put("district", "Sé");
        address.put("city", "São Paulo");
        address.put("state", "SP");
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Maria Exemplo da Silva");
        body.put("cpf", SyntheticData.cpf());
        body.put("birthDate", "1990-05-17");
        body.put("phone", "+5511999990000");
        body.put("address", address);
        changes.forEach((field, value) -> {
            if (field.startsWith("address.")) {
                address.put(field.substring("address.".length()), value);
            } else {
                body.put(field, value);
            }
        });
        return body;
    }

    protected static Map<String, Object> customerBody() {
        return customerBody(Map.of());
    }

    /** Cadastra a conta pelo {@code POST} com o corpo e devolve o id do cadastro. */
    protected UUID register(Account account, Map<String, Object> body) {
        return UUID.fromString(as(account).post(CUSTOMERS, body).getBody().get("id").asString());
    }

    protected int customersOf(Account account) {
        return jdbc.sql("SELECT count(*) FROM customer WHERE identity_subject = :subject")
                .param("subject", account.subject()).query(Integer.class).single();
    }

    protected int audited(String action, UUID id) {
        return jdbc.sql("SELECT count(*) FROM audit_log WHERE action = :action AND entity_id = :id")
                .param("action", action).param("id", id).query(Integer.class).single();
    }

    protected int auditRows() {
        return jdbc.sql("SELECT count(*) FROM audit_log").query(Integer.class).single();
    }

    /** A linha crua do cadastro, como o banco guarda. */
    protected Map<String, Object> rowOf(UUID id) {
        return jdbc.sql("SELECT * FROM customer WHERE id = :id").param("id", id).query().singleRow();
    }

    /** As colunas que mudaram de uma linha crua para a outra, comparando os bytes das cifradas. */
    protected static List<String> changedColumns(Map<String, Object> before, Map<String, Object> after) {
        return before.keySet().stream()
                .filter(column -> !Objects.deepEquals(before.get(column), after.get(column)))
                .toList();
    }

    /** Os {@code fields} do erro como {@code "campo CÓDIGO"}. */
    protected static List<String> fieldsOf(ResponseEntity<JsonNode> response) {
        List<String> fields = new ArrayList<>();
        response.getBody().get("fields").forEach(f -> fields.add(f.get("field").asString() + " " + f.get("code").asString()));
        return fields;
    }

    /** Faz as chamadas em threads que largam juntas e devolve as respostas na ordem das chamadas. */
    @SafeVarargs
    protected static List<ResponseEntity<JsonNode>> atOnce(Callable<ResponseEntity<JsonNode>>... calls)
            throws Exception {
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

    /** {@code ***.982.247-**}, como a resposta mostra o CPF. */
    protected static String maskedCpf(String cpf) {
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }

    /** Uma conta do provedor: {@code email} nulo é token sem e-mail; {@code emailVerified} nulo, sem a marca. */
    protected record Account(String subject, String email, Boolean emailVerified) {

        Account withEmail(String otherEmail, Boolean verified) {
            return new Account(subject, otherEmail, verified);
        }
    }
}
