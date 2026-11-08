package com.revenda.sales.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * O que vai no documento de transferência (DP-06): a compra retirada, o comprador, o veículo e quando foi emitido. Um
 * PDF simplificado com os campos do ATPV-e; não substitui o documento do Detran.
 */
public record TransferDocument(Purchase purchase, TransferBuyer buyer, TransferVehicle vehicle, Instant issuedAt) {

    public TransferDocument {
        Objects.requireNonNull(purchase, "purchase");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (purchase.status() != PurchaseStatus.COMPLETED) {
            throw new IllegalArgumentException("O documento é só da compra retirada");
        }
    }

    /** Onde o PDF fica no bucket: só o id da compra, nada do comprador. */
    public static String keyOf(Purchase purchase) {
        return "purchases/" + purchase.id() + "/transfer-document.pdf";
    }

    @Override
    public String toString() {
        return "TransferDocument[purchaseId=" + purchase.id() + ", dados pessoais omitidos]";
    }
}
