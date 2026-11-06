package com.revenda.sales.domain.ports.in;

import com.revenda.sales.domain.model.PurchaseDetails;
import java.util.UUID;

/**
 * UC-11 (RN-13): o dono desiste da compra. Antes de pagar, a cobrança é cancelada; depois, o pagamento é estornado; nos
 * dois, o veículo volta à venda. Compra de outra conta é {@code NOT_FOUND}.
 */
public interface CancelPurchaseUseCase {

    PurchaseDetails cancel(UUID purchaseId, String customerSubject);
}
