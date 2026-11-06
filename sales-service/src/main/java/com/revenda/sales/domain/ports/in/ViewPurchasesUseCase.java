package com.revenda.sales.domain.ports.in;

import com.revenda.common.paging.Page;
import com.revenda.common.paging.Paging;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.model.PurchaseStatus;
import java.util.UUID;

/**
 * UC-13: acompanhar compras. O cliente vê só as próprias — a de outra conta é {@code NOT_FOUND} (RN-15); a equipe
 * vê todas.
 */
public interface ViewPurchasesUseCase {

    PurchaseDetails view(UUID purchaseId, Viewer viewer);

    Page<Purchase> list(PurchaseStatus status, Paging paging, Viewer viewer);

    /** Quem olha: a conta do token e se é da equipe ({@code SELLER} ou {@code ADMIN}). */
    record Viewer(String subject, boolean staff) {

        @Override
        public String toString() {
            return "Viewer[staff=" + staff + "]";
        }
    }
}
