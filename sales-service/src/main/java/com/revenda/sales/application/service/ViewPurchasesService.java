package com.revenda.sales.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.common.paging.Page;
import com.revenda.common.paging.Paging;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.model.PurchaseFilter;
import com.revenda.sales.domain.model.PurchaseStatus;
import com.revenda.sales.domain.ports.in.ViewPurchasesUseCase;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViewPurchasesService implements ViewPurchasesUseCase {

    private final LoadPurchasePort purchases;

    @Override
    @Transactional(readOnly = true)
    public PurchaseDetails view(UUID purchaseId, Viewer viewer) {
        Purchase purchase = purchases.load(purchaseId)
                .filter(found -> viewer.staff() || found.customerSubject().equals(viewer.subject()))
                .orElseThrow(BusinessException::notFound);
        return new PurchaseDetails(purchase, purchases.loadTransitions(purchaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Purchase> list(PurchaseStatus status, Paging paging, Viewer viewer) {
        return purchases.findPage(new PurchaseFilter(viewer.staff() ? null : viewer.subject(), status), paging);
    }
}
