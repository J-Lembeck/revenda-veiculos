package com.revenda.sales.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.common.error.CommonError;
import com.revenda.common.error.DependencyUnavailableException;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.ports.in.ConfirmPickupUseCase;
import com.revenda.sales.domain.ports.out.CheckBuyerIdentityPort;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A retirada confere a compra antes de chamar o cadastro (a não paga nem gasta uma conferência de CPF), confere o CPF
 * sem transação aberta e só então trava a compra, confere de novo e a conclui. A desistência ou outra retirada que
 * chegam no meio travam a mesma linha: quem chega depois vê a compra já mudada e recebe {@code PURCHASE_NOT_PAID}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmPickupService implements ConfirmPickupUseCase {

    private final LoadPurchasePort purchases;
    private final LockPurchasePort locks;
    private final SavePurchasePort savedPurchases;
    private final CheckBuyerIdentityPort identity;
    private final SagaOutbox outbox;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Override
    public PurchaseDetails confirm(UUID purchaseId, String presentedCpf) {
        Purchase purchase = purchases.load(purchaseId).orElseThrow(BusinessException::notFound);
        purchase.requirePickupAllowed();
        if (!sameBuyer(purchase, presentedCpf)) {
            transaction.executeWithoutResult(status -> audit.record(AuditEntry.of(
                            SalesAuditAction.PURCHASE_PICKUP_REJECTED, Purchase.ENTITY_TYPE, purchaseId,
                            "Retirada recusada: o CPF apresentado não é o do comprador")
                    .withExtra(PurchaseSnapshot.of(purchase))));
            throw BusinessException.of(SalesError.IDENTITY_MISMATCH);
        }
        transaction.executeWithoutResult(status -> {
            Purchase locked = locks.lock(purchaseId).orElseThrow(BusinessException::notFound);
            PurchaseChange change = locked.confirmPickup(clock.instant());
            savedPurchases.update(change);
            outbox.publishAfter(change);
            audit.record(AuditEntry.of(SalesAuditAction.PURCHASE_PICKUP_CONFIRMED, Purchase.ENTITY_TYPE, purchaseId,
                            "Retirada confirmada com o CPF do comprador conferido")
                    .withChange(PurchaseSnapshot.of(locked), PurchaseSnapshot.of(change.purchase())));
        });
        return PurchaseAnswers.details(purchases, purchaseId);
    }

    private boolean sameBuyer(Purchase purchase, String presentedCpf) {
        try {
            return identity.matches(purchase.customerId(), purchase.id(), presentedCpf);
        } catch (DependencyUnavailableException unavailable) {
            log.warn("Retirada da compra {} não confirmada: {}", purchase.id(), unavailable.getMessage());
            throw BusinessException.of(CommonError.DEPENDENCY_UNAVAILABLE);
        }
    }
}
