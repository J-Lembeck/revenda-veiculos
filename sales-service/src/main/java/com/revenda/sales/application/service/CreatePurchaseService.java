package com.revenda.sales.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.common.error.CommonError;
import com.revenda.common.idempotency.IdempotencyStore;
import com.revenda.sales.domain.model.DependencyUnavailableException;
import com.revenda.sales.domain.model.IssuedCharge;
import com.revenda.sales.domain.model.Payer;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.model.PurchaseStatus;
import com.revenda.sales.domain.model.SagaCommand.CancelCharge;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.model.VehicleReservation;
import com.revenda.sales.domain.service.SagaCommands;
import com.revenda.sales.domain.ports.in.CreatePurchaseCommand;
import com.revenda.sales.domain.ports.in.CreatePurchaseUseCase;
import com.revenda.sales.domain.ports.out.CheckEligibilityPort;
import com.revenda.sales.domain.ports.out.IssueChargePort;
import com.revenda.sales.domain.ports.out.LoadPayerPort;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.ReserveVehiclePort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O caminho síncrono da saga, em transações curtas com as chamadas entre elas: cada passo grava antes da chamada
 * seguinte, para a recuperação saber continuar ou desfazer se o pedido morrer no meio. Nenhuma chamada sai com
 * transação aberta, e nenhuma é repetida: a falha encerra a compra e a compensação vai para o outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePurchaseService implements CreatePurchaseUseCase {

    private final CheckEligibilityPort eligibility;
    private final ReserveVehiclePort vehicles;
    private final LoadPayerPort payers;
    private final IssueChargePort charges;
    private final LoadPurchasePort purchases;
    private final LockPurchasePort locks;
    private final SavePurchasePort savedPurchases;
    private final SagaOutbox outbox;
    private final IdempotencyStore idempotency;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;
    private final SalesProperties properties;
    private final Clock clock;

    @Override
    public PurchaseDetails create(CreatePurchaseCommand command) {
        UUID customerId = checkEligibility(command.customerSubject());
        IdempotencyStore.Outcome<RequestedPurchase> requested = transaction.execute(status ->
                idempotency.once(RequestedPurchase.class, () -> request(customerId, command)));
        UUID id = requested.result().id();
        if (!requested.replayed()) {
            reserve(id);
            issueCharge(id);
        }
        return PurchaseAnswers.answer(PurchaseAnswers.details(purchases, id));
    }

    private UUID checkEligibility(String customerSubject) {
        try {
            return eligibility.check(customerSubject).requireEligible();
        } catch (DependencyUnavailableException unavailable) {
            log.warn("Compra não começou: {}", unavailable.getMessage());
            throw BusinessException.of(CommonError.DEPENDENCY_UNAVAILABLE);
        }
    }

    private RequestedPurchase request(UUID customerId, CreatePurchaseCommand command) {
        if (purchases.existsActiveFor(customerId)) {
            throw BusinessException.of(SalesError.ACTIVE_PURCHASE_EXISTS);
        }
        PurchaseChange requested = Purchase.request(UUID.randomUUID(), customerId, command.customerSubject(),
                command.vehicleId(), now());
        savedPurchases.create(requested);
        Purchase purchase = requested.purchase();
        audit.record(AuditEntry.of(SalesAuditAction.PURCHASE_CREATED, Purchase.ENTITY_TYPE, purchase.id(),
                        "Compra pedida")
                .withChange(null, PurchaseSnapshot.of(purchase)));
        return new RequestedPurchase(purchase.id());
    }

    private void reserve(UUID id) {
        Purchase purchase = purchases.load(id).orElseThrow();
        VehicleReservation reservation = vehicles.reserve(purchase.vehicleId(), id);
        transaction.executeWithoutResult(status -> {
            Purchase locked = locks.lock(id).orElseThrow();
            if (locked.status() != PurchaseStatus.PENDING) {
                log.warn("Compra {} saiu de PENDING durante a reserva ({}); a reserva é desfeita", id, locked.status());
                if (reservation instanceof VehicleReservation.Reserved) {
                    outbox.publish(SagaCommands.release(locked));
                }
                return;
            }
            apply(switch (reservation) {
                case VehicleReservation.Reserved reserved -> locked.reserve(reserved.price(), now());
                case VehicleReservation.Unavailable unavailable -> locked.reject(now());
                case VehicleReservation.Unknown unknown -> locked.failReservation(now());
            });
        });
    }

    private void issueCharge(UUID id) {
        Purchase purchase = purchases.load(id).orElseThrow();
        if (purchase.status() != PurchaseStatus.RESERVED) {
            return;
        }
        IssuedCharge charge = requestCharge(purchase);
        transaction.executeWithoutResult(status -> {
            Purchase locked = locks.lock(id).orElseThrow();
            if (locked.status() != PurchaseStatus.RESERVED) {
                log.warn("Compra {} saiu de RESERVED durante a cobrança ({}); a cobrança é cancelada", id,
                        locked.status());
                if (charge != null) {
                    outbox.publish(new CancelCharge(id, charge.chargeId()));
                }
                return;
            }
            apply(charge == null
                    ? locked.failCharge(now())
                    : locked.awaitPayment(charge.chargeId(), charge.pixCode(), charge.dueAt(), now()));
        });
    }

    private IssuedCharge requestCharge(Purchase purchase) {
        Instant dueAt = now().plus(properties.paymentWindow()).truncatedTo(ChronoUnit.SECONDS);
        try {
            Payer payer = payers.load(purchase.customerId(), purchase.id());
            return charges.issue(purchase.id(), purchase.price(), dueAt, payer);
        } catch (DependencyUnavailableException unavailable) {
            log.warn("Cobrança da compra {} não emitida: {}", purchase.id(), unavailable.getMessage());
            return null;
        }
    }

    private void apply(PurchaseChange change) {
        savedPurchases.update(change);
        outbox.publishAfter(change);
    }

    private Instant now() {
        return clock.instant();
    }
}
