package com.revenda.sales.domain.ports.in;

import com.revenda.sales.domain.model.DocumentLink;
import java.util.UUID;

/**
 * O link temporário do documento de transferência, para o dono da compra e para a equipe; de outra conta,
 * {@code NOT_FOUND} (RN-15). Sem documento ainda, {@code DOCUMENT_NOT_READY}. Cada link entregue fica auditado.
 */
public interface GetTransferDocumentUseCase {

    DocumentLink link(UUID purchaseId, ViewPurchasesUseCase.Viewer viewer);
}
