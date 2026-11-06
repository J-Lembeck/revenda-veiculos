package com.revenda.sales.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha da linha do tempo da compra: de onde veio, para onde foi, como ficou o pagamento e por quê. Sem
 * {@code from} na criação; {@code from} igual a {@code to} quando só o pagamento mudou.
 */
public record PurchaseTransition(
        UUID purchaseId,
        PurchaseStatus from,
        PurchaseStatus to,
        PaymentStatus paymentStatus,
        TransitionReason reason,
        Instant occurredAt) {
}
