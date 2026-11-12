package com.revenda.sales.domain.ports.in;

import com.revenda.sales.domain.model.PurchaseDetails;
import java.util.UUID;

/**
 * A equipe confirma a retirada da compra paga, depois de conferir que o CPF do documento apresentado é o
 * do comprador ({@code IDENTITY_MISMATCH} se não é). É o pivô da saga: a compra fica {@code COMPLETED}, e a baixa no
 * estoque e o documento de transferência saem pelo outbox.
 */
public interface ConfirmPickupUseCase {

    PurchaseDetails confirm(UUID purchaseId, String presentedCpf);
}
