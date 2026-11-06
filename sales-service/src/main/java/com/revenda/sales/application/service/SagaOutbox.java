package com.revenda.sales.application.service;

import com.revenda.common.outbox.NewOutboxMessage;
import com.revenda.common.outbox.OutboxPublisher;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.SagaCommand;
import com.revenda.sales.domain.model.SagaCommand.CancelCharge;
import com.revenda.sales.domain.model.SagaCommand.DeliverVehicle;
import com.revenda.sales.domain.model.SagaCommand.MarkVehicleSold;
import com.revenda.sales.domain.model.SagaCommand.RefundCharge;
import com.revenda.sales.domain.model.SagaCommand.ReleaseVehicle;
import com.revenda.sales.domain.service.SagaCommands;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Grava no outbox, na transação da mudança da compra, os comandos que ela pede. O corpo de cada comando é o que o
 * participante lê: ids, centavos e instantes, nunca dado pessoal.
 */
@Component
@RequiredArgsConstructor
public class SagaOutbox {

    private final OutboxPublisher outbox;

    public void publishAfter(PurchaseChange change) {
        SagaCommands.after(change).forEach(this::publish);
    }

    public void publish(SagaCommand command) {
        outbox.publish(new NewOutboxMessage(command.destination()::name, command.type(), command.purchaseId(),
                command.groupKey(), payloadOf(command)));
    }

    static Record payloadOf(SagaCommand command) {
        return switch (command) {
            case ReleaseVehicle release -> new VehiclePayload(release.vehicleId(), release.purchaseId());
            case MarkVehicleSold sale -> new SalePayload(sale.vehicleId(), sale.purchaseId(),
                    sale.soldPrice().toCents(), sale.soldAt());
            case DeliverVehicle delivery -> new DeliveryPayload(delivery.vehicleId(), delivery.purchaseId(),
                    delivery.deliveredAt());
            case CancelCharge cancel -> new ChargePayload(cancel.purchaseId(), cancel.chargeId());
            case RefundCharge refund -> new ChargePayload(refund.purchaseId(), refund.chargeId());
        };
    }

    record VehiclePayload(UUID vehicleId, UUID purchaseId) {
    }

    record SalePayload(UUID vehicleId, UUID purchaseId, long soldPriceCents, Instant soldAt) {
    }

    record DeliveryPayload(UUID vehicleId, UUID purchaseId, Instant deliveredAt) {
    }

    record ChargePayload(UUID purchaseId, String chargeId) {
    }
}
