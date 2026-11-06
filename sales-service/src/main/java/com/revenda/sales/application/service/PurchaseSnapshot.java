package com.revenda.sales.application.service;

import com.revenda.sales.domain.model.ClosedReason;
import com.revenda.sales.domain.model.PaymentStatus;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseStatus;
import java.util.UUID;

/** O retrato da compra na auditoria: o veículo, o status e a versão, nada do cliente. */
record PurchaseSnapshot(UUID vehicleId, PurchaseStatus status, PaymentStatus paymentStatus,
        ClosedReason closedReason, int version) {

    static PurchaseSnapshot of(Purchase purchase) {
        return new PurchaseSnapshot(purchase.vehicleId(), purchase.status(), purchase.paymentStatus(),
                purchase.closedReason(), purchase.version());
    }
}
