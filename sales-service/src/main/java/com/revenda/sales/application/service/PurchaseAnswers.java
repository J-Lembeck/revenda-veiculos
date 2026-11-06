package com.revenda.sales.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.CommonError;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import java.util.UUID;

/**
 * A resposta do pedido de compra sai do estado gravado — o do pedido que acabou de rodar e o da repetição com a mesma
 * {@code Idempotency-Key}: recusada na reserva, {@code VEHICLE_UNAVAILABLE}; encerrada porque um vizinho não
 * respondeu, {@code DEPENDENCY_UNAVAILABLE}; ainda no meio, {@code PURCHASE_IN_PROGRESS}; o resto, a compra como está.
 */
final class PurchaseAnswers {

    private PurchaseAnswers() {
    }

    static PurchaseDetails details(LoadPurchasePort purchases, UUID id) {
        Purchase purchase = purchases.load(id).orElseThrow(BusinessException::notFound);
        return new PurchaseDetails(purchase, purchases.loadTransitions(id));
    }

    static PurchaseDetails answer(PurchaseDetails details) {
        Purchase purchase = details.purchase();
        switch (purchase.status()) {
            case PENDING, RESERVED -> throw BusinessException.of(SalesError.PURCHASE_IN_PROGRESS);
            case REJECTED -> throw BusinessException.of(SalesError.VEHICLE_UNAVAILABLE);
            case FAILED -> {
                switch (purchase.closedReason()) {
                    case RESERVATION_FAILED, CHARGE_FAILED, RECOVERY_TIMEOUT ->
                            throw BusinessException.of(CommonError.DEPENDENCY_UNAVAILABLE);
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
        return details;
    }
}
