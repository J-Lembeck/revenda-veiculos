package com.revenda.sales.domain.model;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;

/**
 * A compra de um veículo por um cliente, o agregado da saga. Cada transição devolve a compra na versão seguinte com a
 * linha da linha do tempo que a acompanha. O preço é o que a reserva devolveu (RN-09) e não muda depois; do cliente só
 * ficam o id e o {@code sub} da conta.
 *
 * <p>O passo do orquestrador fora de ordem é erro de programa ({@link IllegalStateException}); o pedido do cliente
 * fora de hora sai com o código da API; o evento do gateway e a varredura que acham a compra já mudada não fazem nada,
 * e o evento que chega cedo demais ou contradiz a compra sai com um código só de log.
 */
@Builder(toBuilder = true)
public record Purchase(
        UUID id,
        UUID customerId,
        String customerSubject,
        UUID vehicleId,
        Money price,
        PurchaseStatus status,
        PaymentStatus paymentStatus,
        String chargeId,
        String paymentCode,
        Instant paymentDueAt,
        Instant paidAt,
        Money paidAmount,
        Instant completedAt,
        ClosedReason closedReason,
        String documentKey,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static final String ENTITY_TYPE = "PURCHASE";

    /** T1: o cliente pediu o veículo; a compra fica pendente, sem preço nem pagamento, enquanto a reserva corre. */
    public static PurchaseChange request(UUID id, UUID customerId, String customerSubject, UUID vehicleId,
            Instant now) {
        Purchase purchase = new Purchase(Objects.requireNonNull(id, "id"), Objects.requireNonNull(customerId,
                "customerId"), requireText(customerSubject, "customerSubject"), Objects.requireNonNull(vehicleId,
                "vehicleId"), null, PurchaseStatus.PENDING, PaymentStatus.NONE, null, null, null, null, null, null, null,
                null, 0, now, now);
        return new PurchaseChange(purchase, new PurchaseTransition(id, null, PurchaseStatus.PENDING, PaymentStatus.NONE,
                TransitionReason.PURCHASE_REQUESTED, now));
    }

    /** T2: o veículo ficou preso a esta compra, pelo preço que a própria reserva devolveu (RN-09). */
    public PurchaseChange reserve(Money reservedPrice, Instant now) {
        requireStep(PurchaseStatus.PENDING, "reservar");
        if (reservedPrice == null || !reservedPrice.isPositive()) {
            throw new IllegalArgumentException("O preço da reserva é maior que zero");
        }
        return moveTo(next(PurchaseStatus.RESERVED, paymentStatus, now).price(reservedPrice),
                TransitionReason.VEHICLE_RESERVED);
    }

    /** Outra compra reservou antes, ou o veículo não está à venda (RN-08): nada a desfazer. */
    public PurchaseChange reject(Instant now) {
        requireStep(PurchaseStatus.PENDING, "recusar a reserva");
        return close(next(PurchaseStatus.REJECTED, paymentStatus, now), ClosedReason.VEHICLE_UNAVAILABLE);
    }

    /** O estoque não respondeu à reserva: não se sabe se reservou, então a compra falha e o veículo é liberado. */
    public PurchaseChange failReservation(Instant now) {
        requireStep(PurchaseStatus.PENDING, "falhar a reserva");
        return close(next(PurchaseStatus.FAILED, paymentStatus, now), ClosedReason.RESERVATION_FAILED);
    }

    /** T3: a cobrança foi emitida e o código de pagamento vai para o cliente. */
    public PurchaseChange awaitPayment(String issuedChargeId, String issuedPaymentCode, Instant dueAt, Instant now) {
        requireStep(PurchaseStatus.RESERVED, "aguardar o pagamento");
        return moveTo(next(PurchaseStatus.AWAITING_PAYMENT, PaymentStatus.PENDING, now)
                        .chargeId(requireText(issuedChargeId, "chargeId"))
                        .paymentCode(requireText(issuedPaymentCode, "paymentCode"))
                        .paymentDueAt(Objects.requireNonNull(dueAt, "dueAt")),
                TransitionReason.CHARGE_ISSUED);
    }

    /** A cobrança não saiu: a compra falha e o veículo é liberado. */
    public PurchaseChange failCharge(Instant now) {
        requireStep(PurchaseStatus.RESERVED, "falhar a cobrança");
        return close(next(PurchaseStatus.FAILED, paymentStatus, now), ClosedReason.CHARGE_FAILED);
    }

    /**
     * UC-11 (RN-13): antes de pagar, a cobrança é cancelada; depois, o pagamento é estornado. Durante a reserva, o
     * cliente tenta de novo; depois de encerrada ou retirada, não há desistência.
     */
    public PurchaseChange cancel(Instant now) {
        return switch (status) {
            case AWAITING_PAYMENT -> close(next(PurchaseStatus.CANCELLED, PaymentStatus.CANCELLED, now),
                    ClosedReason.CUSTOMER_GAVE_UP);
            case PAID -> close(next(PurchaseStatus.CANCELLED, PaymentStatus.REFUND_REQUESTED, now),
                    ClosedReason.CUSTOMER_GAVE_UP);
            case PENDING, RESERVED -> throw BusinessException.of(SalesError.PURCHASE_IN_PROGRESS);
            case COMPLETED, REJECTED, FAILED, EXPIRED, CANCELLED ->
                    throw BusinessException.of(SalesError.PURCHASE_NOT_CANCELLABLE);
        };
    }

    /** UC-12 (RN-14): a retirada da compra paga. A conferência do CPF vem antes e fica fora da compra. */
    public PurchaseChange confirmPickup(Instant now) {
        if (status != PurchaseStatus.PAID) {
            throw BusinessException.of(SalesError.PURCHASE_NOT_PAID);
        }
        return moveTo(next(PurchaseStatus.COMPLETED, paymentStatus, now).completedAt(now),
                TransitionReason.PICKUP_CONFIRMED);
    }

    /**
     * UC-09 (RN-12): o gateway avisou o pagamento desta cobrança. Aguardando, com o valor cobrado, a compra é paga — mesmo
     * passado o vencimento, se a varredura ainda não a expirou; com outro valor, falha e pede o estorno. Encerrada, pede o
     * estorno e não revive. Vazio se o pagamento já foi registrado.
     */
    public Optional<PurchaseChange> recordPayment(String paidChargeId, Money amount, Instant paidAtGateway,
            Instant now) {
        requireSameCharge(paidChargeId);
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("O valor pago é maior que zero");
        }
        Objects.requireNonNull(paidAtGateway, "paidAt");
        if (status.isInFlight()) {
            throw BusinessException.of(SalesError.PAYMENT_EVENT_NOT_YET_APPLICABLE);
        }
        if (paymentStatus.isReceived()) {
            return Optional.empty();
        }
        if (status == PurchaseStatus.AWAITING_PAYMENT) {
            return Optional.of(amount.equals(price)
                    ? moveTo(next(PurchaseStatus.PAID, PaymentStatus.PAID, now).paidAt(paidAtGateway).paidAmount(amount),
                            TransitionReason.PAYMENT_CONFIRMED)
                    : close(next(PurchaseStatus.FAILED, PaymentStatus.REFUND_REQUESTED, now).paidAt(paidAtGateway)
                            .paidAmount(amount), ClosedReason.PAYMENT_MISMATCH));
        }
        return Optional.of(moveTo(next(status, PaymentStatus.REFUND_REQUESTED, now).paidAt(paidAtGateway)
                .paidAmount(amount).chargeId(paidChargeId), TransitionReason.LATE_PAYMENT));
    }

    /** O gateway confirmou o estorno pedido; a compra fica como está. Vazio se já estava estornada. */
    public Optional<PurchaseChange> recordRefund(String refundedChargeId, Instant now) {
        requireSameCharge(refundedChargeId);
        return switch (paymentStatus) {
            case REFUND_REQUESTED -> Optional.of(moveTo(next(status, PaymentStatus.REFUNDED, now),
                    TransitionReason.REFUND_CONFIRMED));
            case REFUNDED -> Optional.empty();
            case NONE, PENDING, PAID, CANCELLED -> throw BusinessException.of(SalesError.PAYMENT_EVENT_CONTRADICTORY);
        };
    }

    /**
     * UC-10 (RN-11): vencido o prazo sem pagamento, a compra expira, a cobrança é cancelada e o veículo liberado. Vazio
     * se ela já saiu de aguardando ou o prazo ainda não venceu.
     */
    public Optional<PurchaseChange> expire(Instant now) {
        if (status != PurchaseStatus.AWAITING_PAYMENT || !now.isAfter(paymentDueAt)) {
            return Optional.empty();
        }
        return Optional.of(close(next(PurchaseStatus.EXPIRED, PaymentStatus.CANCELLED, now),
                ClosedReason.PAYMENT_EXPIRED));
    }

    /**
     * A compra parada no meio do caminho síncrono há mais de {@code threshold}: o pedido morreu, então ela falha e a
     * reserva e a cobrança, se existirem, são desfeitas. Vazio se ela andou ou ainda está dentro do limite.
     */
    public Optional<PurchaseChange> recover(Duration threshold, Instant now) {
        if (!status.isInFlight() || !updatedAt.isBefore(now.minus(threshold))) {
            return Optional.empty();
        }
        return Optional.of(close(next(PurchaseStatus.FAILED, paymentStatus, now), ClosedReason.RECOVERY_TIMEOUT));
    }

    private PurchaseBuilder next(PurchaseStatus newStatus, PaymentStatus newPaymentStatus, Instant now) {
        return toBuilder().status(newStatus).paymentStatus(newPaymentStatus).version(version + 1)
                .updatedAt(Objects.requireNonNull(now, "now"));
    }

    private PurchaseChange close(PurchaseBuilder next, ClosedReason reason) {
        return moveTo(next.closedReason(reason), TransitionReason.closing(reason));
    }

    private PurchaseChange moveTo(PurchaseBuilder next, TransitionReason reason) {
        Purchase moved = next.build();
        return new PurchaseChange(moved, new PurchaseTransition(id, status, moved.status, moved.paymentStatus, reason,
                moved.updatedAt));
    }

    private void requireStep(PurchaseStatus expected, String step) {
        if (status != expected) {
            throw new IllegalStateException("A compra " + id + " está em " + status + "; " + step + " só a partir de "
                    + expected);
        }
    }

    private void requireSameCharge(String eventChargeId) {
        requireText(eventChargeId, "chargeId");
        if (chargeId != null && !chargeId.equals(eventChargeId)) {
            throw BusinessException.of(SalesError.PAYMENT_EVENT_CONTRADICTORY);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " é obrigatório");
        }
        return value;
    }

    @Override
    public String toString() {
        return "Purchase[id=" + id + ", vehicleId=" + vehicleId + ", status=" + status + ", paymentStatus="
                + paymentStatus + ", closedReason=" + closedReason + ", version=" + version + "]";
    }

    public static class PurchaseBuilder {

        @Override
        public String toString() {
            return "PurchaseBuilder[id=" + id + ", status=" + status + "]";
        }
    }
}
