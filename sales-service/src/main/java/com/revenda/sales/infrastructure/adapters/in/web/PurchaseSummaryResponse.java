package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.sales.domain.model.ClosedReason;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A compra na listagem: os mesmos campos do detalhe, sem a linha do tempo.")
record PurchaseSummaryResponse(
        @Schema(example = "5b7d2c1e-8a4f-4d3b-9e6a-1c2b3d4e5f60") UUID id,
        @Schema(example = "3f2b8c4e-1a7d-4e9b-8c2f-6d5a4b3c2e10") UUID customerId,
        @Schema(example = "7c1e4b2a-9f3d-4e8a-b6c5-2d1f0a9e8b7c") UUID vehicleId,
        @Schema(example = "PAID") PurchaseStatus status,
        @Schema(nullable = true, example = "CUSTOMER_GAVE_UP") ClosedReason closedReason,
        @Schema(nullable = true, example = "8990000") Long priceCents,
        PurchaseResponse.PaymentResponse payment,
        @Schema(example = "2026-11-06T14:02:00Z") Instant createdAt,
        @Schema(example = "2026-11-06T14:10:00Z") Instant updatedAt) {

    static PurchaseSummaryResponse of(Purchase purchase) {
        return new PurchaseSummaryResponse(purchase.id(), purchase.customerId(), purchase.vehicleId(),
                purchase.status(), purchase.closedReason(), PurchaseResponse.cents(purchase.price()),
                PurchaseResponse.PaymentResponse.of(purchase), purchase.createdAt(), purchase.updatedAt());
    }
}
