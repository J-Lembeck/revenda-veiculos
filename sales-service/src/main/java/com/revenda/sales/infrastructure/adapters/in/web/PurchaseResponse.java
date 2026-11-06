package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.common.money.Money;
import com.revenda.sales.domain.model.ClosedReason;
import com.revenda.sales.domain.model.PaymentStatus;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.model.PurchaseStatus;
import com.revenda.sales.domain.model.PurchaseTransition;
import com.revenda.sales.domain.model.TransitionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A compra com a linha do tempo. Do cliente, só o id do cadastro; dinheiro em centavos.")
record PurchaseResponse(
        @Schema(example = "5b7d2c1e-8a4f-4d3b-9e6a-1c2b3d4e5f60") UUID id,
        @Schema(description = "Id do cadastro do comprador", example = "3f2b8c4e-1a7d-4e9b-8c2f-6d5a4b3c2e10")
        UUID customerId,
        @Schema(example = "7c1e4b2a-9f3d-4e8a-b6c5-2d1f0a9e8b7c") UUID vehicleId,
        @Schema(example = "AWAITING_PAYMENT") PurchaseStatus status,
        @Schema(description = "Por que a compra encerrou; nulo se não encerrou", nullable = true,
                example = "PAYMENT_EXPIRED") ClosedReason closedReason,
        @Schema(description = "Preço do veículo no momento da reserva, em centavos; nulo antes da reserva",
                nullable = true, example = "8990000") Long priceCents,
        PaymentResponse payment,
        @Schema(example = "2026-11-06T14:02:00Z") Instant createdAt,
        @Schema(example = "2026-11-06T14:02:01Z") Instant updatedAt,
        @Schema(description = "Cada mudança da compra, na ordem; `reason` é o código do motivo") List<Step> timeline) {

    static PurchaseResponse of(PurchaseDetails details) {
        Purchase purchase = details.purchase();
        return new PurchaseResponse(purchase.id(), purchase.customerId(), purchase.vehicleId(), purchase.status(),
                purchase.closedReason(), cents(purchase.price()), PaymentResponse.of(purchase), purchase.createdAt(),
                purchase.updatedAt(), details.timeline().stream().map(Step::of).toList());
    }

    static Long cents(Money money) {
        return money == null ? null : money.toCents();
    }

    @Schema(description = "O pagamento: o código Pix (sintético) e até quando vale; o que foi pago, quando chegou")
    record PaymentResponse(
            @Schema(example = "PENDING") PaymentStatus status,
            @Schema(description = "Pix copia e cola; nulo antes da cobrança", nullable = true,
                    example = "00020126580014br.gov.bcb.pix0116pix@example.test5204000053039865409089900.005802BR"
                            + "5916REVENDA SIMULADA6009SAO PAULO62070503***6304ABCD") String code,
            @Schema(nullable = true, example = "2026-11-06T14:32:00Z") Instant dueAt,
            @Schema(nullable = true, example = "2026-11-06T14:10:00Z") Instant paidAt,
            @Schema(nullable = true, example = "8990000") Long paidAmountCents) {

        static PaymentResponse of(Purchase purchase) {
            return new PaymentResponse(purchase.paymentStatus(), purchase.paymentCode(), purchase.paymentDueAt(),
                    purchase.paidAt(), cents(purchase.paidAmount()));
        }
    }

    @Schema(description = "Uma linha da linha do tempo")
    record Step(
            @Schema(example = "RESERVED") PurchaseStatus status,
            @Schema(example = "NONE") PaymentStatus paymentStatus,
            @Schema(example = "VEHICLE_RESERVED") TransitionReason reason,
            @Schema(example = "2026-11-06T14:02:01Z") Instant at) {

        static Step of(PurchaseTransition transition) {
            return new Step(transition.to(), transition.paymentStatus(), transition.reason(),
                    transition.occurredAt());
        }
    }
}
