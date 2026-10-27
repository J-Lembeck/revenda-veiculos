package com.revenda.vehicles.infrastructure.adapters.in.messaging;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * Lê a fila em <i>long polling</i> numa thread própria e apaga só a mensagem tratada. Mensagem que fica na fila
 * segura as seguintes do mesmo grupo (o veículo) nesta leva, para nenhuma ser aplicada fora de ordem.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "revenda.vehicle-commands.enabled", havingValue = "true")
@RequiredArgsConstructor
class VehicleCommandListener implements SmartLifecycle {

    private static final int BATCH_SIZE = 10;

    private final SqsClient sqs;
    private final VehicleCommandsProperties properties;
    private final VehicleCommandHandler handler;

    private volatile boolean running;
    private Thread worker;

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().name("vehicle-commands").daemon().start(this::poll);
    }

    @Override
    public void stop() {
        running = false;
        worker.interrupt();
        try {
            worker.join(properties.waitTime().plus(properties.retryDelay()).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void poll() {
        while (running) {
            try {
                receiveOnce();
            } catch (RuntimeException e) {
                if (running) {
                    log.warn("Fila de comandos do estoque sem resposta ({}); nova tentativa em {}",
                            e.getClass().getSimpleName(), properties.retryDelay());
                    pause();
                }
            }
        }
    }

    private void receiveOnce() {
        List<Message> messages = sqs.receiveMessage(request -> request
                        .queueUrl(properties.queueUrl())
                        .maxNumberOfMessages(BATCH_SIZE)
                        .waitTimeSeconds((int) properties.waitTime().toSeconds())
                        .messageSystemAttributeNames(MessageSystemAttributeName.MESSAGE_GROUP_ID,
                                MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                .messages();
        Set<String> heldGroups = new HashSet<>();
        for (Message message : messages) {
            String group = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
            if (heldGroups.contains(group)) {
                continue;
            }
            if (handler.handle(message.body(), receiveCount(message))) {
                sqs.deleteMessage(request -> request.queueUrl(properties.queueUrl())
                        .receiptHandle(message.receiptHandle()));
            } else {
                heldGroups.add(group);
            }
        }
    }

    private static int receiveCount(Message message) {
        String count = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        return count == null ? 1 : Integer.parseInt(count);
    }

    private void pause() {
        try {
            Thread.sleep(properties.retryDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
