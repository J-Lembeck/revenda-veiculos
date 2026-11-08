package com.revenda.sales;

import com.revenda.common.outbox.OutboxDispatcher;
import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.TestTokenConfiguration;
import com.revenda.common.test.TestTokens;
import com.revenda.common.webhook.WebhookSignature;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.sales.application.service.SalesProperties;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchasePaths;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * O serviço de pé, com o banco de teste, a fila FIFO no LocalStack, tokens de teste e os vizinhos de mentira
 * ({@link FakeNeighbors}), chamado pelo HTTP. Os jobs ficam desligados — o teste chama o despachante e a varredura
 * quando quer — e o limite de taxa também, menos no teste dele.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestTokenConfiguration.class, SalesTestSqs.class})
@TestPropertySource(properties = {
        "revenda.security.oidc.issuer-uri=" + TestTokens.ISSUER,
        "revenda.security.rate-limit.enabled=false"})
abstract class SalesApiTest {

    protected static final String PURCHASES = "/purchases";
    protected static final String WEBHOOK = "/webhooks/payments";
    protected static final String SELLER = "5d1e7a40-vendedora";
    protected static final FakeNeighbors NEIGHBORS = FakeNeighbors.shared();
    protected static final Duration PATIENCE = Duration.ofSeconds(20);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected OutboxDispatcher dispatcher;

    @Autowired
    protected SalesProperties properties;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected Clock clock;

    @Autowired
    private SavePurchasePort savedPurchases;

    @Autowired
    protected TransactionTemplate transaction;

    @DynamicPropertySource
    static void neighbors(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "sales");
        String url = NEIGHBORS.url().toString();
        registry.add("revenda.neighbors.vehicles-url", () -> url);
        registry.add("revenda.neighbors.customers-url", () -> url);
        registry.add("revenda.neighbors.gateway-url", () -> url);
        registry.add("revenda.neighbors.read-timeout", () -> "500ms");
        registry.add("spring.security.oauth2.client.provider.revenda.token-uri", () -> url + "/token");
        registry.add("revenda.vehicle-commands.queue-url", SalesTestSqs.QUEUE::url);
        SalesTestS3.register(registry);
    }

    @BeforeEach
    void neighborsAnswerAsTheRealOnes() {
        NEIGHBORS.reset();
    }

    protected ApiDriver api() {
        return new ApiDriver(rest);
    }

    protected ApiDriver customer(String subject) {
        return api().asCustomer(subject);
    }

    /** Uma conta nova de comprador, com cadastro completo e maior de idade no cadastro de mentira. */
    protected static String newCustomer() {
        return UUID.randomUUID().toString();
    }

    protected ResponseEntity<JsonNode> buy(String subject, UUID vehicleId) {
        return customer(subject).post(PURCHASES, Map.of("vehicleId", vehicleId));
    }

    /** Compra um veículo novo e devolve o id da compra, que fica aguardando o pagamento. */
    protected UUID awaitingPurchase(String subject) {
        ResponseEntity<JsonNode> created = buy(subject, UUID.randomUUID());
        if (!created.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Compra não criada: " + created.getStatusCode() + " " + created.getBody());
        }
        return UUID.fromString(created.getBody().get("id").asString());
    }

    /** Compra um veículo novo e paga, pelo webhook; devolve o id da compra, que fica esperando a retirada. */
    protected UUID paidPurchase(String subject) {
        UUID id = awaitingPurchase(subject);
        ResponseEntity<JsonNode> paid = pay(id);
        if (!paid.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Pagamento não aplicado: " + paid.getStatusCode());
        }
        return id;
    }

    protected ApiDriver seller() {
        return api().asSeller(SELLER);
    }

    /** A equipe confirma a retirada com o CPF do documento apresentado. */
    protected ResponseEntity<JsonNode> pickup(UUID purchaseId, String cpf) {
        return seller().post(PURCHASES + "/" + purchaseId + "/pickup", Map.of("cpf", cpf));
    }

    protected ResponseEntity<JsonNode> documentOf(ApiDriver viewer, UUID purchaseId) {
        return viewer.get(PURCHASES + "/" + purchaseId + "/document");
    }

    /** Compra, paga e retira com o CPF do comprador; o documento ainda não saiu (o outbox não foi despachado). */
    protected UUID completedPurchase(String subject) {
        UUID id = paidPurchase(subject);
        ResponseEntity<JsonNode> picked = pickup(id, FakeNeighbors.BUYER_CPF);
        if (!picked.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Retirada não confirmada: " + picked.getStatusCode() + " " + picked.getBody());
        }
        return id;
    }

    /** Baixa pelo link assinado, como o navegador do comprador: sem token, só o endereço. */
    protected static HttpResponse<byte[]> download(URI url) {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /** O texto da primeira página do PDF. */
    protected static String pdfText(byte[] pdf) {
        try (PdfReader reader = new PdfReader(pdf)) {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Grava a compra do caminho com todas as mudanças dele, como se a saga tivesse andado até ali. */
    protected Purchase persist(PurchasePaths.Path path) {
        ActorContextHolder.callAs(ActorContext.system(), () -> transaction.execute(status -> {
            savedPurchases.create(path.changes().getFirst());
            path.changes().stream().skip(1).forEach(savedPurchases::update);
            return null;
        }));
        return path.purchase();
    }

    /** O cliente dono da compra gravada, com o {@code sub} dela. */
    protected ApiDriver ownerOf(Purchase purchase) {
        return customer(purchase.customerSubject());
    }

    protected Map<String, Object> purchaseRow(UUID id) {
        return jdbc.sql("SELECT * FROM purchase WHERE id = :id").param("id", id).query().singleRow();
    }

    protected String statusOf(UUID id) {
        return (String) purchaseRow(id).get("status");
    }

    protected List<String> timelineOf(UUID id) {
        return jdbc.sql("SELECT to_status || '/' || payment_status || '/' || reason FROM purchase_transition "
                        + "WHERE purchase_id = :id ORDER BY id")
                .param("id", id).query(String.class).list();
    }

    /** As mensagens do outbox da compra, na ordem de gravação, como {@code "DESTINO Tipo"}. */
    protected List<String> outboxOf(UUID purchaseId) {
        return jdbc.sql("SELECT destination || ' ' || type FROM outbox_message WHERE aggregate_id = :id ORDER BY seq")
                .param("id", purchaseId).query(String.class).list();
    }

    protected List<Map<String, Object>> outboxRowsOf(UUID purchaseId) {
        return jdbc.sql("SELECT id, destination, type, group_key, payload::text AS payload, status, attempts "
                        + "FROM outbox_message WHERE aggregate_id = :id ORDER BY seq")
                .param("id", purchaseId).query().listOfRows();
    }

    protected int audited(String action, UUID id) {
        return jdbc.sql("SELECT count(*) FROM audit_log WHERE action = :action AND entity_id = :id")
                .param("action", action).param("id", id).query(Integer.class).single();
    }

    /** Despacha o outbox até não sobrar nada devido agora. */
    protected void dispatch() {
        OutboxDispatcher.DispatchRun run;
        do {
            run = dispatcher.dispatchDue();
        } while (run.sent() > 0);
    }

    /** O webhook como o gateway manda: o corpo assinado com o segredo e o carimbo de agora. */
    protected ResponseEntity<JsonNode> webhook(String body) {
        return webhook(body, new WebhookSignature(properties.webhookSecret()).sign(bytes(body), clock.instant()));
    }

    protected ResponseEntity<JsonNode> webhook(String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (signature != null) {
            headers.set(WebhookSignature.HEADER, signature);
        }
        return rest.exchange(WEBHOOK, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected static String paymentEvent(String eventId, String type, String chargeId, Object reference, long cents) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"chargeId\":\"" + chargeId
                + "\",\"referenceId\":\"" + reference + "\",\"amountCents\":" + cents
                + ",\"occurredAt\":\"2026-11-06T14:10:00Z\"}";
    }

    protected static String newEventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    protected String chargeIdOf(UUID purchaseId) {
        return (String) purchaseRow(purchaseId).get("charge_id");
    }

    /** O gateway avisa que a cobrança da compra foi paga com o valor dado. */
    protected ResponseEntity<JsonNode> pay(UUID purchaseId, long cents) {
        NEIGHBORS.markPaid(purchaseId, cents);
        return webhook(paymentEvent(newEventId(), "CHARGE_PAID", NEIGHBORS.chargeOf(purchaseId).chargeId(),
                purchaseId, cents));
    }

    protected ResponseEntity<JsonNode> pay(UUID purchaseId) {
        return pay(purchaseId, FakeNeighbors.DEFAULT_PRICE_CENTS);
    }

    /**
     * Os comandos que chegaram à fila FIFO para o veículo, na ordem da fila, como o estoque os recebe: o envelope, o
     * grupo e a deduplicação. Espera até chegarem {@code expected}; tira da fila tudo o que leu, inclusive o que
     * sobrou de outros testes.
     */
    protected List<QueuedCommand> queuedFor(UUID vehicleId, int expected) {
        List<QueuedCommand> received = new ArrayList<>();
        Awaitility.await().atMost(PATIENCE).until(() -> {
            List<Message> messages = SalesTestSqs.SQS.receiveMessage(request -> request
                    .queueUrl(SalesTestSqs.QUEUE.url())
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .messageSystemAttributeNames(MessageSystemAttributeName.MESSAGE_GROUP_ID,
                            MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID)).messages();
            for (Message message : messages) {
                String group = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
                if (vehicleId.toString().equals(group)) {
                    received.add(new QueuedCommand(json.readTree(message.body()), group,
                            message.attributes().get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID)));
                }
                SalesTestSqs.SQS.deleteMessage(request -> request.queueUrl(SalesTestSqs.QUEUE.url())
                        .receiptHandle(message.receiptHandle()));
            }
            return received.size() >= expected;
        });
        return received;
    }

    protected static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Faz as chamadas em threads que largam juntas e devolve as respostas na ordem das chamadas. */
    @SafeVarargs
    protected static <T> List<T> atOnce(Callable<T>... calls) throws Exception {
        CyclicBarrier start = new CyclicBarrier(calls.length);
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return call.call();
                }));
            }
            List<T> answers = new ArrayList<>();
            for (Future<T> future : futures) {
                answers.add(future.get(30, TimeUnit.SECONDS));
            }
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Um comando como chegou à fila: o envelope {@code { id, type, payload }}, o grupo e a deduplicação. */
    protected record QueuedCommand(JsonNode envelope, String groupId, String deduplicationId) {

        String type() {
            return envelope.get("type").asString();
        }
    }
}
