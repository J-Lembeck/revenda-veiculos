package com.revenda.sales.infrastructure.adapters.out.messaging;

import com.revenda.common.outbox.OutboxDestination;
import com.revenda.common.outbox.OutboxMessage;
import com.revenda.common.outbox.OutboxSender;
import com.revenda.sales.domain.model.CommandDestination;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Manda o comando do estoque para a fila FIFO no envelope {@code { id, type, payload }}, com o veículo como grupo
 * (ordem por veículo) e o id da mensagem do outbox como deduplicação — o mesmo envio repetido entra uma vez.
 */
@Component
@RequiredArgsConstructor
class SqsVehicleCommandSender implements OutboxSender {

    private final SqsClient sqs;
    private final VehicleCommandsProperties properties;
    private final ObjectMapper json;

    @Override
    public OutboxDestination destination() {
        return CommandDestination.VEHICLE_COMMANDS::name;
    }

    @Override
    public void send(OutboxMessage message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Envio para a fila com transação aberta");
        }
        String body = json.writeValueAsString(
                new Envelope(message.id(), message.type(), json.readTree(message.payload())));
        sqs.sendMessage(request -> request
                .queueUrl(properties.queueUrl())
                .messageBody(body)
                .messageGroupId(message.groupKey())
                .messageDeduplicationId(message.id().toString()));
    }

    record Envelope(UUID id, String type, JsonNode payload) {
    }
}
