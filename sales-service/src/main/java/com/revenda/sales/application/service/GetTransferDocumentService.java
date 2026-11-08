package com.revenda.sales.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.sales.domain.model.DocumentLink;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.ports.in.GetTransferDocumentUseCase;
import com.revenda.sales.domain.ports.in.ViewPurchasesUseCase.Viewer;
import com.revenda.sales.domain.ports.out.LinkTransferDocumentPort;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O link do documento é gerado a cada pedido, assinado e curto (R16): quem baixa é quem acabou de pedir. Cada link
 * entregue grava {@code PURCHASE_DOCUMENT_DOWNLOADED} com quem pediu — o documento tem CPF e endereço.
 */
@Service
@RequiredArgsConstructor
public class GetTransferDocumentService implements GetTransferDocumentUseCase {

    private final LoadPurchasePort purchases;
    private final LinkTransferDocumentPort links;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;

    @Override
    public DocumentLink link(UUID purchaseId, Viewer viewer) {
        Purchase purchase = purchases.load(purchaseId)
                .filter(found -> viewer.staff() || found.customerSubject().equals(viewer.subject()))
                .orElseThrow(BusinessException::notFound);
        if (purchase.documentKey() == null) {
            throw BusinessException.of(SalesError.DOCUMENT_NOT_READY);
        }
        DocumentLink link = links.link(purchase.documentKey());
        transaction.executeWithoutResult(status -> audit.record(AuditEntry.of(
                        SalesAuditAction.PURCHASE_DOCUMENT_DOWNLOADED, Purchase.ENTITY_TYPE, purchaseId,
                        "Link temporário do documento de transferência entregue")
                .withExtra(new LinkSnapshot(link.expiresAt()))));
        return link;
    }

    record LinkSnapshot(Instant expiresAt) {
    }
}
