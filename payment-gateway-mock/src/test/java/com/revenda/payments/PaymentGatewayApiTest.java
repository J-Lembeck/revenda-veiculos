package com.revenda.payments;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.SyntheticData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O gateway de pé, chamado pelo HTTP com a chave de API de teste, e o webhook indo para o {@link WebhookReceiver}, com
 * as reentregas em milissegundos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
        PaymentGatewayApiTest.API_KEY_PROPERTY,
        PaymentGatewayApiTest.WEBHOOK_SECRET_PROPERTY,
        PaymentGatewayApiTest.RETRY_DELAYS_PROPERTY})
abstract class PaymentGatewayApiTest {

    static final String API_KEY = "chave-de-teste-do-gateway";
    static final String WEBHOOK_SECRET = "segredo-de-teste-do-webhook";
    static final String API_KEY_PROPERTY = "revenda.gateway.api-key=" + API_KEY;
    static final String WEBHOOK_SECRET_PROPERTY = "revenda.gateway.webhook-secret=" + WEBHOOK_SECRET;
    static final String RETRY_DELAYS_PROPERTY = "revenda.gateway.webhook-retry-delays=50ms,100ms,150ms";
    protected static final String CHARGES = "/charges";
    protected static final long AMOUNT_CENTS = 4_590_000L;
    protected static final String PAYER_NAME = "Pagador Sintético";
    protected static final String STREET = "Rua das Amostras";

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void webhook(DynamicPropertyRegistry registry) {
        registerWebhook(registry);
    }

    static void registerWebhook(DynamicPropertyRegistry registry) {
        registry.add("revenda.gateway.webhook-url", () -> WebhookReceiver.shared().url().toString());
    }

    /** O condutor com a chave de API certa, como o {@code sales} chama. */
    protected ApiDriver api() {
        return anonymous().withHeader("X-Api-Key", API_KEY);
    }

    protected ApiDriver anonymous() {
        return new ApiDriver(rest);
    }

    protected WebhookReceiver receiver() {
        return WebhookReceiver.shared();
    }

    /** Um pedido de cobrança válido, com referência nova e pagador sintético; {@code changes} troca o que vier. */
    protected static Map<String, Object> chargeBody(Map<String, Object> changes) {
        Map<String, Object> address = new HashMap<>();
        address.put("zipCode", "01310000");
        address.put("street", STREET);
        address.put("number", "100");
        address.put("district", "Centro");
        address.put("city", "São Paulo");
        address.put("state", "SP");
        Map<String, Object> payer = new HashMap<>();
        payer.put("name", PAYER_NAME);
        payer.put("cpf", SyntheticData.cpf());
        payer.put("address", address);
        Map<String, Object> body = new HashMap<>();
        body.put("referenceId", UUID.randomUUID().toString());
        body.put("amountCents", AMOUNT_CENTS);
        body.put("dueAt", Instant.now().plus(Duration.ofMinutes(30)).toString());
        body.put("payer", payer);
        changes.forEach((field, value) -> {
            if (field.startsWith("payer.address.")) {
                address.put(field.substring("payer.address.".length()), value);
            } else if (field.startsWith("payer.")) {
                payer.put(field.substring("payer.".length()), value);
            } else {
                body.put(field, value);
            }
        });
        return body;
    }

    protected static Map<String, Object> chargeBody() {
        return chargeBody(Map.of());
    }

    /** Cria a cobrança do corpo e devolve o {@code chargeId}. */
    protected String create(Map<String, Object> body) {
        return api().post(CHARGES, body).getBody().get("chargeId").asString();
    }

    protected JsonNode chargeOf(Map<String, Object> body) {
        return api().get(CHARGES + "?referenceId=" + body.get("referenceId")).getBody();
    }

    protected static String pathOf(String chargeId, String action) {
        return CHARGES + "/" + chargeId + "/" + action;
    }

    /** Os eventos do tipo que o webhook entregou para a cobrança, um por entrega, na ordem de chegada. */
    protected List<JsonNode> eventsOf(String chargeId, String type) {
        List<JsonNode> events = new ArrayList<>();
        for (WebhookReceiver.Delivery delivery : receiver().all()) {
            JsonNode event = JsonMapper.shared().readTree(delivery.body());
            if (event.get("chargeId").asString().equals(chargeId) && event.get("type").asString().equals(type)) {
                events.add(event);
            }
        }
        return events;
    }

    /** Os {@code fields} do erro como {@code "campo CÓDIGO"}. */
    protected static List<String> fieldsOf(ResponseEntity<JsonNode> response) {
        List<String> fields = new ArrayList<>();
        response.getBody().get("fields")
                .forEach(f -> fields.add(f.get("field").asString() + " " + f.get("code").asString()));
        return fields;
    }
}
