package com.revenda.sales.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.common.messaging.ProcessedMessages;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.ports.in.PaymentEventCommand;
import com.revenda.sales.domain.ports.in.ReceivePaymentEventUseCase;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O aviso do gateway, numa transação: guarda o {@code eventId} (o repetido não faz nada), trava a compra da
 * referência e aplica o evento. A mudança vai com os comandos que ela pede: vender o veículo, ou estornar e liberar.
 */
@Service
@RequiredArgsConstructor
public class ReceivePaymentEventService implements ReceivePaymentEventUseCase {

    static final String PAYMENT_WEBHOOK = "payment-webhook";

    private final ProcessedMessages processed;
    private final LockPurchasePort locks;
    private final SavePurchasePort savedPurchases;
    private final SagaOutbox outbox;
    private final Clock clock;

    @Override
    @Transactional
    public Outcome receive(PaymentEventCommand event) {
        Instant now = clock.instant();
        if (!processed.markProcessed(event.eventId(), PAYMENT_WEBHOOK, now)) {
            return Outcome.DUPLICATE;
        }
        Purchase purchase = purchaseId(event.referenceId())
                .flatMap(locks::lock)
                .orElseThrow(() -> BusinessException.of(SalesError.PAYMENT_EVENT_CONTRADICTORY));
        Optional<PurchaseChange> change = switch (event.type()) {
            case CHARGE_PAID -> purchase.recordPayment(event.chargeId(), event.amount(), event.occurredAt(), now);
            case CHARGE_REFUNDED -> purchase.recordRefund(event.chargeId(), now);
        };
        change.ifPresent(applied -> {
            savedPurchases.update(applied);
            outbox.publishAfter(applied);
        });
        return change.isPresent() ? Outcome.APPLIED : Outcome.NO_EFFECT;
    }

    private static Optional<UUID> purchaseId(String referenceId) {
        try {
            return Optional.of(UUID.fromString(referenceId));
        } catch (IllegalArgumentException notAPurchase) {
            return Optional.empty();
        }
    }
}
