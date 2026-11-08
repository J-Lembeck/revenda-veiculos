package com.revenda.sales.infrastructure.adapters.in.messaging;

import com.revenda.common.outbox.OutboxDestination;
import com.revenda.common.outbox.OutboxMessage;
import com.revenda.common.outbox.OutboxSender;
import com.revenda.sales.domain.model.CommandDestination;
import com.revenda.sales.domain.model.SagaCommand.GenerateTransferDocument;
import com.revenda.sales.domain.ports.in.GenerateTransferDocumentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * O destino {@code DOCUMENTS} do outbox é o próprio {@code sales}: o despachante entrega aqui o pedido de documento
 * da compra retirada, sem transação aberta, e a falha volta para o outbox com espera, como a de qualquer vizinho.
 */
@Component
@RequiredArgsConstructor
class TransferDocumentCommandSender implements OutboxSender {

    private final GenerateTransferDocumentUseCase generateDocument;

    @Override
    public OutboxDestination destination() {
        return CommandDestination.DOCUMENTS::name;
    }

    @Override
    public void send(OutboxMessage message) {
        if (!GenerateTransferDocument.TYPE.equals(message.type())) {
            throw new IllegalArgumentException("Comando de documento desconhecido: " + message.type());
        }
        generateDocument.generate(message.aggregateId());
    }
}
