package com.revenda.sales.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.ports.in.CancelPurchaseUseCase;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A desistência trava a compra e confere o status, então disputa a linha com o webhook e a varredura: quem chega
 * depois vê a compra já mudada — se a desistência venceu, o pagamento que chega em seguida vira estorno.
 */
@Service
@RequiredArgsConstructor
public class CancelPurchaseService implements CancelPurchaseUseCase {

    private final LockPurchasePort locks;
    private final LoadPurchasePort purchases;
    private final SavePurchasePort savedPurchases;
    private final SagaOutbox outbox;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public PurchaseDetails cancel(UUID purchaseId, String customerSubject) {
        Purchase purchase = locks.lock(purchaseId)
                .filter(locked -> locked.customerSubject().equals(customerSubject))
                .orElseThrow(BusinessException::notFound);
        PurchaseChange change = purchase.cancel(clock.instant());
        savedPurchases.update(change);
        outbox.publishAfter(change);
        audit.record(AuditEntry.of(SalesAuditAction.PURCHASE_CANCELLED, Purchase.ENTITY_TYPE, purchaseId,
                        "Compra cancelada pelo cliente")
                .withChange(PurchaseSnapshot.of(purchase), PurchaseSnapshot.of(change.purchase())));
        return PurchaseAnswers.details(purchases, purchaseId);
    }
}
