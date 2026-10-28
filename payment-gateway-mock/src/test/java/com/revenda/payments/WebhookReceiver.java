package com.revenda.payments;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * O {@code sales} de mentira: recebe o webhook numa porta livre, guarda cada entrega e responde o próximo status da
 * fila combinada pelo teste ({@code 200} quando ela acaba).
 */
final class WebhookReceiver {

    private static final WebhookReceiver SHARED = new WebhookReceiver();

    private final HttpServer server;
    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private final Deque<Integer> answers = new ConcurrentLinkedDeque<>();

    private WebhookReceiver() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        server.createContext("/webhooks/payments", exchange -> {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            deliveries.add(new Delivery(exchange.getRequestHeaders().getFirst("X-Signature"),
                    exchange.getRequestHeaders().getFirst("Content-Type"), body, Instant.now()));
            Integer status = answers.poll();
            exchange.sendResponseHeaders(status == null ? 200 : status, -1);
            exchange.close();
        });
        server.start();
    }

    static WebhookReceiver shared() {
        return SHARED;
    }

    URI url() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks/payments");
    }

    /** As próximas respostas, na ordem; depois delas, {@code 200}. */
    void answer(Integer... statuses) {
        answers.clear();
        answers.addAll(List.of(statuses));
    }

    void reset() {
        answers.clear();
        deliveries.clear();
    }

    /** As entregas do evento, na ordem em que chegaram. */
    List<Delivery> deliveriesOf(String eventId) {
        List<Delivery> found = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            if (delivery.text().contains("\"" + eventId + "\"")) {
                found.add(delivery);
            }
        }
        return found;
    }

    List<Delivery> all() {
        return List.copyOf(deliveries);
    }

    record Delivery(String signature, String contentType, byte[] body, Instant receivedAt) {

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
