package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;
import org.awaitility.Awaitility;

import com.revenda.common.money.Money;
import com.revenda.common.test.LocalStackTestSqs;
import com.revenda.common.test.LocalStackTestSqs.FifoQueue;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldCommand;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldUseCase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * O consumidor ligado a uma fila FIFO própria no LocalStack, com DLQ depois de 5 recebimentos como a do ambiente
 * local e <i>visibility timeout</i> de 2 s para a mensagem recusada voltar logo.
 */
@Import(VehicleCommandQueueTests.QueueOnLocalStack.class)
class VehicleCommandQueueTests extends VehiclesApiTest {

    private static final Duration VISIBILITY_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration PATIENCE = Duration.ofSeconds(30);
    private static final SqsClient SQS = LocalStackTestSqs.client();
    private static final FifoQueue QUEUE = LocalStackTestSqs.createFifoQueue(SQS, "vehicle-commands", 5,
            VISIBILITY_TIMEOUT);

    @Autowired
    private MarkVehicleSoldUseCase markVehicleSold;

    @DynamicPropertySource
    static void queue(DynamicPropertyRegistry registry) {
        registry.add("revenda.vehicle-commands.enabled", () -> "true");
        registry.add("revenda.vehicle-commands.queue-url", QUEUE::url);
        registry.add("revenda.vehicle-commands.wait-time", () -> "1s");
        registry.add("revenda.vehicle-commands.retry-delay", () -> "1s");
    }

    @AfterAll
    static void closeClient() {
        SQS.close();
    }

    @Test
    void aCommandFromTheQueueIsAppliedAndLeavesTheQueue() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        send(vehicle, sale(UUID.randomUUID(), vehicle, purchase));

        Awaitility.await().atMost(PATIENCE).until(() -> "SOLD".equals(row(vehicle).get("status")));
        Awaitility.await().atMost(PATIENCE).until(() -> messagesIn(QUEUE.url()) == 0);
        assertThat(row(vehicle)).containsEntry("sold_price_cents", 8_990_000L);
    }

    @Test
    void theSameCommandDeliveredTwiceIsAppliedOnce() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        String body = sale(UUID.randomUUID(), vehicle, purchase);

        send(vehicle, body);
        send(vehicle, body);

        Awaitility.await().atMost(PATIENCE).until(() -> "SOLD".equals(row(vehicle).get("status")));
        Awaitility.await().atMost(PATIENCE).until(() -> messagesIn(QUEUE.url()) == 0);
        assertThat(row(vehicle)).containsEntry("version", 2);
        assertThat(audited("VEHICLE_SOLD", vehicle)).isEqualTo(1);
    }

    @Test
    void theSaleThenTheReleaseOfTheSameVehicleArriveInOrder() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        send(vehicle, sale(UUID.randomUUID(), vehicle, purchase));
        send(vehicle, release(UUID.randomUUID(), vehicle, purchase));

        Awaitility.await().atMost(PATIENCE).until(() -> audited("VEHICLE_RELEASED", vehicle) == 1);
        assertThat(row(vehicle)).containsEntry("status", "AVAILABLE").containsEntry("sold_price_cents", null)
                .containsEntry("version", 3);
        assertThat(audited("VEHICLE_SOLD", vehicle)).isEqualTo(1);
    }

    @Test
    void aDeliveryBeforeTheSaleStaysInTheQueueAndIsAppliedOnceTheVehicleIsSold() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        send(vehicle, delivery(UUID.randomUUID(), vehicle, purchase));

        Awaitility.await().during(VISIBILITY_TIMEOUT).atMost(PATIENCE)
                .until(() -> "RESERVED".equals(row(vehicle).get("status")) && messagesIn(QUEUE.url()) == 1);
        markVehicleSold.markSold(new MarkVehicleSoldCommand(UUID.randomUUID(), vehicle, purchase,
                Money.ofCents(8_990_000L), Instant.parse("2026-10-27T13:05:00Z")));

        Awaitility.await().atMost(PATIENCE).until(() -> "DELIVERED".equals(row(vehicle).get("status")));
        Awaitility.await().atMost(PATIENCE).until(() -> messagesIn(QUEUE.url()) == 0);
        assertThat(audited("VEHICLE_DELIVERED", vehicle)).isEqualTo(1);
    }

    @Test
    void aContradictoryCommandIsNotAppliedAndEndsInTheDeadLetterQueue() {
        UUID vehicle = register().id();
        UUID message = UUID.randomUUID();

        send(vehicle, sale(message, vehicle, UUID.randomUUID()));

        Awaitility.await().atMost(PATIENCE).until(() -> deadLettersMention(message.toString()));
        assertThat(row(vehicle)).containsEntry("status", "AVAILABLE").containsEntry("version", 0);
        assertThat(audited("VEHICLE_SOLD", vehicle)).isZero();
    }

    @Test
    void anUnreadableMessageEndsInTheDeadLetterQueue() {
        UUID vehicle = register().id();
        String marker = UUID.randomUUID().toString();

        send(vehicle, "{\"id\":\"" + marker + "\",\"type\":\"MarkVehicleSold\"}");

        Awaitility.await().atMost(PATIENCE).until(() -> deadLettersMention(marker));
        assertThat(row(vehicle)).containsEntry("status", "AVAILABLE");
    }

    private static void send(UUID vehicle, String body) {
        SQS.sendMessage(request -> request.queueUrl(QUEUE.url())
                .messageGroupId(vehicle.toString())
                .messageDeduplicationId(UUID.randomUUID().toString())
                .messageBody(body));
    }

    private static String sale(UUID message, UUID vehicle, UUID purchase) {
        return envelope(message, "MarkVehicleSold", """
                {"vehicleId":"%s","purchaseId":"%s","soldPriceCents":8990000,"soldAt":"2026-10-27T13:05:00Z"}"""
                .formatted(vehicle, purchase));
    }

    private static String release(UUID message, UUID vehicle, UUID purchase) {
        return envelope(message, "ReleaseVehicle", """
                {"vehicleId":"%s","purchaseId":"%s"}""".formatted(vehicle, purchase));
    }

    private static String delivery(UUID message, UUID vehicle, UUID purchase) {
        return envelope(message, "DeliverVehicle", """
                {"vehicleId":"%s","purchaseId":"%s","deliveredAt":"2026-10-29T10:30:00Z"}"""
                .formatted(vehicle, purchase));
    }

    private static String envelope(UUID message, String type, String payload) {
        return "{\"id\":\"" + message + "\",\"type\":\"" + type + "\",\"payload\":" + payload + "}";
    }

    private static int messagesIn(String queueUrl) {
        Map<QueueAttributeName, String> attributes = SQS.getQueueAttributes(request -> request.queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).attributes();
        return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    /** Lê a DLQ sem apagar: o que foi lido volta a ficar visível para a próxima conferência. */
    private static boolean deadLettersMention(String marker) {
        List<Message> dead = SQS.receiveMessage(request -> request.queueUrl(QUEUE.deadLetterUrl())
                .maxNumberOfMessages(10).visibilityTimeout(0)).messages();
        return dead.stream().anyMatch(message -> message.body().contains(marker));
    }

    static class QueueOnLocalStack {

        @Bean(destroyMethod = "close")
        SqsClient sqsClient() {
            return LocalStackTestSqs.client();
        }
    }
}
