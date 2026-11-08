package com.revenda.customers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O serviço de vendas e o provedor de identidade de mentira, numa porta livre: a retenção de cada cliente responde
 * nula, a não ser que o teste combine uma data ou uma falha. Guarda cada pedido de retenção com o token que veio.
 */
final class FakeSales {

    private static final FakeSales SHARED = new FakeSales();
    private static final Pattern RETENTION = Pattern.compile("/internal/customers/([^/]+)/retention");
    private static final String SERVICE_TOKEN = "token-de-servico-do-cadastro";

    private final HttpServer server;
    private final Map<UUID, Instant> retainUntil = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile int failWith;

    private FakeSales() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        server.setExecutor(Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("vendas-", 1).factory()));
        server.createContext("/", this::handle);
        server.start();
    }

    static FakeSales shared() {
        return SHARED;
    }

    URI url() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    void reset() {
        retainUntil.clear();
        failWith = 0;
    }

    void retains(UUID customerId, Instant until) {
        retainUntil.put(customerId, until);
    }

    /** Toda retenção responde com esse status, como um serviço fora do ar. */
    void failsWith(int status) {
        failWith = status;
    }

    List<Call> retentionCallsFor(UUID customerId) {
        return calls.stream().filter(call -> call.customerId().equals(customerId)).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && path.equals("/token")) {
                reply(exchange, 200, "{\"access_token\":\"" + SERVICE_TOKEN
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
                return;
            }
            Matcher retention = RETENTION.matcher(path);
            if ("GET".equals(exchange.getRequestMethod()) && retention.matches()) {
                UUID customerId = UUID.fromString(retention.group(1));
                calls.add(new Call(customerId, exchange.getRequestHeaders().getFirst("Authorization")));
                if (failWith != 0) {
                    reply(exchange, failWith, "{\"code\":\"INTERNAL_ERROR\"}");
                    return;
                }
                Instant until = retainUntil.get(customerId);
                reply(exchange, 200, "{\"retainUntil\":" + (until == null ? "null" : "\"" + until + "\"") + "}");
                return;
            }
            reply(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    record Call(UUID customerId, String authorization) {

        boolean withServiceToken() {
            return ("Bearer " + SERVICE_TOKEN).equals(authorization);
        }
    }
}
