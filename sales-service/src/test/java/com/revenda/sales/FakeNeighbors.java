package com.revenda.sales;

import com.revenda.common.test.SyntheticData;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * O estoque, o cadastro, o gateway e o provedor de identidade de mentira, numa porta livre, para o caminho síncrono da
 * saga: cada vizinho responde como o de verdade, a não ser que o teste combine outra resposta — um status, um atraso
 * maior que o prazo de leitura, um veículo já reservado. Guarda cada chamada recebida.
 */
final class FakeNeighbors {

    static final long DEFAULT_PRICE_CENTS = 8_990_000;
    static final String PIX_CODE = "00020126580014br.gov.bcb.pix0116pix@example.test5802BR6304ABCD";

    private static final FakeNeighbors SHARED = new FakeNeighbors();
    private static final Pattern RESERVATION = Pattern.compile("/internal/vehicles/([^/]+)/reservations");
    private static final Pattern ELIGIBILITY = Pattern.compile("/internal/customers/by-subject/([^/]+)/eligibility");
    private static final Pattern PAYER = Pattern.compile("/internal/customers/([^/]+)/payer");
    private static final Pattern CHARGE_COMMAND = Pattern.compile("/charges/([^/]+)/(cancel|refund)");

    private final HttpServer server;
    private final ObjectMapper json = JsonMapper.builder().build();
    private final AtomicInteger tokens = new AtomicInteger();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<UUID, Reply> vehicles = new ConcurrentHashMap<>();
    private final Map<String, Reply> eligibility = new ConcurrentHashMap<>();
    private final Map<String, Charge> charges = new ConcurrentHashMap<>();
    private volatile Reply payer;
    private volatile Reply chargeCreation;
    private volatile Duration chargeCreatedButLate = Duration.ZERO;
    private volatile Reply chargeCommands;
    private volatile Reply token;

    private FakeNeighbors() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        server.setExecutor(Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("vizinho-", 1).factory()));
        server.createContext("/", this::handle);
        server.start();
    }

    static FakeNeighbors shared() {
        return SHARED;
    }

    URI url() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Volta tudo ao padrão: cada vizinho responde como o de verdade. As chamadas e as cobranças ficam. */
    void reset() {
        vehicles.clear();
        eligibility.clear();
        payer = null;
        chargeCreation = null;
        chargeCreatedButLate = Duration.ZERO;
        chargeCommands = null;
        token = null;
    }

    void vehicleAnswers(UUID vehicleId, Reply reply) {
        vehicles.put(vehicleId, reply);
    }

    void eligibilityAnswers(String subject, Reply reply) {
        eligibility.put(subject, reply);
    }

    void payerAnswers(Reply reply) {
        payer = reply;
    }

    void chargeCreationAnswers(Reply reply) {
        chargeCreation = reply;
    }

    /** O gateway cria a cobrança, mas a resposta demora {@code wait} — passado o prazo de leitura, ela se perde. */
    void chargeCreatedButAnswersAfter(Duration wait) {
        chargeCreatedButLate = wait;
    }

    void chargeCommandsAnswer(Reply reply) {
        chargeCommands = reply;
    }

    void tokenAnswers(Reply reply) {
        token = reply;
    }

    /** O id do cadastro que o cadastro de mentira dá a cada conta. */
    static UUID customerIdOf(String subject) {
        return UUID.nameUUIDFromBytes(("cliente " + subject).getBytes(StandardCharsets.UTF_8));
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    List<Call> callsTo(String pathPrefix) {
        return calls.stream().filter(call -> call.path().startsWith(pathPrefix)).toList();
    }

    Charge chargeOf(UUID purchaseId) {
        return charges.get(purchaseId.toString());
    }

    /** O gateway marca a cobrança como paga, como se o Pix tivesse caído, com o valor pago. */
    void markPaid(UUID purchaseId, long paidCents) {
        charges.computeIfPresent(purchaseId.toString(), (reference, charge) -> charge.with("PAID", paidCents));
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            calls.add(new Call(exchange.getRequestMethod(), path, query,
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Api-Key"),
                    exchange.getRequestHeaders().getFirst("X-Purchase-Id"),
                    new String(body, StandardCharsets.UTF_8)));
            reply(exchange, route(exchange.getRequestMethod(), path, query, body));
        }
    }

    private Reply route(String method, String path, String query, byte[] body) {
        Matcher matcher;
        if ("POST".equals(method) && path.equals("/token")) {
            return token != null ? token
                    : Reply.json(200, "{\"access_token\":\"token-de-servico-" + tokens.incrementAndGet()
                            + "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
        }
        if ("POST".equals(method) && (matcher = RESERVATION.matcher(path)).matches()) {
            Reply configured = vehicles.get(UUID.fromString(matcher.group(1)));
            return configured != null ? configured
                    : Reply.json(200, "{\"priceCents\":" + DEFAULT_PRICE_CENTS + "}");
        }
        if ("GET".equals(method) && (matcher = ELIGIBILITY.matcher(path)).matches()) {
            String subject = matcher.group(1);
            Reply configured = eligibility.get(subject);
            return configured != null ? configured
                    : Reply.json(200, "{\"customerId\":\"" + customerIdOf(subject)
                            + "\",\"eligible\":true,\"reason\":null}");
        }
        if ("GET".equals(method) && (matcher = PAYER.matcher(path)).matches()) {
            return payer != null ? payer : Reply.json(200, """
                    {"customerId":"%s","fullName":"Maria Exemplo da Silva","cpf":"%s",
                     "address":{"zipCode":"01001000","street":"Praça da Sé","number":"100","complement":"apto 1",
                                "district":"Sé","city":"São Paulo","state":"SP"}}""".formatted(matcher.group(1),
                    SyntheticData.cpf()));
        }
        if ("POST".equals(method) && path.equals("/charges")) {
            return chargeCreation != null ? chargeCreation : createCharge(body);
        }
        if ("GET".equals(method) && path.equals("/charges")) {
            Charge charge = query == null ? null : charges.get(query.replace("referenceId=", ""));
            return charge == null ? Reply.json(404, "{\"code\":\"NOT_FOUND\"}") : Reply.json(200, charge.toJson());
        }
        if ("POST".equals(method) && (matcher = CHARGE_COMMAND.matcher(path)).matches()) {
            return chargeCommands != null ? chargeCommands : chargeCommand(matcher.group(1), matcher.group(2));
        }
        return Reply.json(404, "{\"code\":\"NOT_FOUND\"}");
    }

    private Reply createCharge(byte[] body) {
        JsonNode request = json.readTree(body);
        String reference = request.get("referenceId").asString();
        boolean[] created = {false};
        Charge charge = charges.computeIfAbsent(reference, key -> {
            created[0] = true;
            return new Charge("chg_" + UUID.randomUUID().toString().replace("-", ""), key,
                    request.get("amountCents").asLong(), null, "PENDING", request.get("dueAt").asString());
        });
        return new Reply(created[0] ? 201 : 200, "{\"chargeId\":\"" + charge.chargeId() + "\",\"pixCode\":\""
                + PIX_CODE + "\",\"dueAt\":\"" + charge.dueAt() + "\"}", chargeCreatedButLate);
    }

    private Reply chargeCommand(String chargeId, String command) {
        Charge charge = charges.values().stream().filter(found -> found.chargeId().equals(chargeId)).findFirst()
                .orElse(null);
        if (charge == null) {
            return Reply.json(404, "{\"code\":\"NOT_FOUND\"}");
        }
        Charge changed = switch (command) {
            case "cancel" -> "PENDING".equals(charge.status()) ? charge.with("CANCELLED", null) : charge;
            default -> "PAID".equals(charge.status()) ? charge.with("REFUNDED", charge.paidCents()) : charge;
        };
        charges.put(charge.referenceId(), changed);
        return Reply.json(200, changed.toJson());
    }

    private static void reply(HttpExchange exchange, Reply reply) throws IOException {
        if (!reply.delay().isZero()) {
            try {
                Thread.sleep(reply.delay());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        } catch (IOException callerGaveUpAfterItsReadTimeout) {
            exchange.close();
        }
    }

    /** Uma resposta combinada: o status, o corpo e quanto demora para sair. */
    record Reply(int status, String body, Duration delay) {

        static Reply json(int status, String body) {
            return new Reply(status, body, Duration.ZERO);
        }

        static Reply status(int status) {
            return json(status, "{\"code\":\"INTERNAL_ERROR\"}");
        }

        static Reply price(long cents) {
            return json(200, "{\"priceCents\":" + cents + "}");
        }

        static Reply unavailableVehicle() {
            return json(409, "{\"code\":\"VEHICLE_UNAVAILABLE\"}");
        }

        static Reply ineligible(String reason, UUID customerId) {
            return json(200, "{\"customerId\":" + (customerId == null ? "null" : "\"" + customerId + "\"")
                    + ",\"eligible\":false,\"reason\":\"" + reason + "\"}");
        }

        Reply after(Duration wait) {
            return new Reply(status, body, wait);
        }
    }

    /** Uma chamada que chegou: método, caminho, consulta, os cabeçalhos que importam e o corpo. */
    record Call(String method, String path, String query, String authorization, String apiKey, String purchaseId,
            String body) {
    }

    record Charge(String chargeId, String referenceId, long amountCents, Long paidCents, String status,
            String dueAt) {

        Charge with(String newStatus, Long paid) {
            return new Charge(chargeId, referenceId, amountCents, paid, newStatus, dueAt);
        }

        String toJson() {
            return "{\"chargeId\":\"" + chargeId + "\",\"referenceId\":\"" + referenceId + "\",\"amountCents\":"
                    + amountCents + ",\"paidAmountCents\":" + paidCents + ",\"pixCode\":\"" + PIX_CODE
                    + "\",\"dueAt\":\"" + dueAt + "\",\"status\":\"" + status + "\"}";
        }
    }
}
