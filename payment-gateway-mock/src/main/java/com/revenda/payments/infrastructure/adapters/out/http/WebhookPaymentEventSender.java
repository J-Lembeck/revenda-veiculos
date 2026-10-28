package com.revenda.payments.infrastructure.adapters.out.http;

import com.revenda.common.webhook.WebhookSignature;
import com.revenda.payments.domain.model.PaymentEvent;
import com.revenda.payments.domain.ports.out.PublishPaymentEventPort;
import com.revenda.payments.infrastructure.config.GatewayProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * O webhook em segundo plano: cada entrega é assinada na hora do envio e, sem {@code 2xx} do destino, tentada de novo
 * depois de cada espera de {@code webhookRetryDelays}. As entregas de um mesmo evento (o duplicado) saem juntas. A
 * fila é em memória: o que estava para ser reentregue se perde se o gateway parar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class WebhookPaymentEventSender implements PublishPaymentEventPort, DisposableBean {

    private final GatewayProperties properties;
    private final WebhookSignature signature;
    private final HttpClient webhookHttpClient;
    private final ObjectMapper json;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            Thread.ofPlatform().name("webhook-", 1).daemon().factory());

    @Override
    public void publish(PaymentEvent event, int deliveries) {
        byte[] body = json.writeValueAsBytes(WebhookBody.of(event));
        for (int delivery = 0; delivery < deliveries; delivery++) {
            scheduler.execute(() -> attempt(event.id(), body, 0));
        }
    }

    private void attempt(String eventId, byte[] body, int retry) {
        int status = send(body);
        if (status >= 200 && status < 300) {
            log.info("Webhook {} entregue na tentativa {}", eventId, retry + 1);
            return;
        }
        List<Duration> delays = properties.webhookRetryDelays();
        if (retry >= delays.size()) {
            log.warn("Webhook {} não entregue depois de {} tentativas; última resposta {}", eventId, retry + 1, status);
            return;
        }
        Duration delay = delays.get(retry);
        log.warn("Webhook {} sem 2xx (resposta {}); nova tentativa em {}", eventId, status, delay);
        scheduler.schedule(() -> attempt(eventId, body, retry + 1), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private int send(byte[] body) {
        Instant now = clock.instant();
        HttpRequest request = HttpRequest.newBuilder(properties.webhookUrl())
                .timeout(properties.webhookTimeout())
                .header("Content-Type", "application/json")
                .header(WebhookSignature.HEADER, signature.sign(body, now))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            return webhookHttpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException failure) {
            log.warn("Webhook sem resposta do destino: {}", failure.getClass().getSimpleName());
            return -1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    /** O corpo do webhook, como o {@code sales} recebe: só ids, centavos e o instante. */
    record WebhookBody(String eventId, String type, String chargeId, String referenceId, long amountCents,
            Instant occurredAt) {

        static WebhookBody of(PaymentEvent event) {
            return new WebhookBody(event.id(), event.type().name(), event.chargeId(), event.referenceId(),
                    event.amount().toCents(), event.occurredAt());
        }
    }
}
