package com.revenda.sales.domain.model;

import com.revenda.common.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Cada caminho da máquina de estados da compra, do pedido até onde ele para, com todas as mudanças no meio. Os
 * instantes andam de segundo em segundo a partir de {@link #REQUESTED_AT}, sem fração abaixo do microssegundo, para
 * a compra relida do banco ser igual à do domínio.
 */
public final class PurchasePaths {

    public static final Instant REQUESTED_AT = Instant.parse("2026-11-01T12:00:00Z");
    public static final Money PRICE = Money.ofCents(8_990_000);
    public static final Money WRONG_AMOUNT = Money.ofCents(8_989_900);
    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);
    public static final Duration RECOVERY_THRESHOLD = Duration.ofMinutes(2);
    public static final String PAYMENT_CODE = "00020126580014br.gov.bcb.pix0116pix@example.test6304ABCD";

    private PurchasePaths() {
    }

    public static Path pending() {
        UUID id = UUID.randomUUID();
        return new Path("pendente", List.of(Purchase.request(id, UUID.randomUUID(), UUID.randomUUID().toString(),
                UUID.randomUUID(), REQUESTED_AT)));
    }

    public static Path reserved() {
        return pending().then("reservada", (purchase, at) -> purchase.reserve(PRICE, at));
    }

    public static Path awaiting() {
        return reserved().then("aguardando pagamento", (purchase, at) ->
                purchase.awaitPayment(chargeIdOf(purchase), PAYMENT_CODE, at.plus(PAYMENT_WINDOW), at));
    }

    public static Path paid() {
        return awaiting().thenMaybe("paga", (purchase, at) -> purchase.recordPayment(purchase.chargeId(), PRICE, at, at));
    }

    public static Path completed() {
        return paid().then("retirada", (purchase, at) -> purchase.confirmPickup(at));
    }

    public static Path rejected() {
        return pending().then("recusada na reserva", (purchase, at) -> purchase.reject(at));
    }

    public static Path reservationFailed() {
        return pending().then("sem resposta da reserva", (purchase, at) -> purchase.failReservation(at));
    }

    public static Path chargeFailed() {
        return reserved().then("cobrança não emitida", (purchase, at) -> purchase.failCharge(at));
    }

    public static Path recoveredWhilePending() {
        return pending().thenMaybe("recuperada pendente", PurchasePaths::recover);
    }

    public static Path recoveredWhileReserved() {
        return reserved().thenMaybe("recuperada reservada", PurchasePaths::recover);
    }

    public static Path expired() {
        return awaiting().thenMaybe("expirada", (purchase, at) -> purchase.expire(purchase.paymentDueAt().plusSeconds(1)));
    }

    public static Path cancelledBeforePaying() {
        return awaiting().then("desistência antes de pagar", (purchase, at) -> purchase.cancel(at));
    }

    public static Path cancelledAfterPaying() {
        return paid().then("desistência depois de pagar", (purchase, at) -> purchase.cancel(at));
    }

    public static Path paidWrongAmount() {
        return awaiting().thenMaybe("paga com valor errado", (purchase, at) ->
                purchase.recordPayment(purchase.chargeId(), WRONG_AMOUNT, at, at));
    }

    public static Path paidLate(Path closed) {
        return closed.thenMaybe("com pagamento atrasado", (purchase, at) ->
                purchase.recordPayment(chargeIdOf(purchase), PRICE, at, at));
    }

    public static Path refunded(Path refundRequested) {
        return refundRequested.thenMaybe("estornada", (purchase, at) -> purchase.recordRefund(purchase.chargeId(), at));
    }

    /** As compras encerradas sem pagamento: as que recebem o pagamento atrasado. */
    public static List<Path> closedUnpaid() {
        return List.of(rejected(), reservationFailed(), chargeFailed(), recoveredWhilePending(),
                recoveredWhileReserved(), expired(), cancelledBeforePaying());
    }

    /** As compras com estorno pedido, de cada caminho que pede. */
    public static List<Path> refundRequested() {
        List<Path> paths = new ArrayList<>(List.of(cancelledAfterPaying(), paidWrongAmount()));
        closedUnpaid().forEach(closed -> paths.add(paidLate(closed)));
        return paths;
    }

    /** Todos os caminhos da tabela, cada um até onde para e cada um com uma compra própria. */
    public static List<Path> all() {
        List<Path> paths = new ArrayList<>(List.of(pending(), reserved(), awaiting(), paid(), completed()));
        paths.addAll(closedUnpaid());
        paths.addAll(refundRequested());
        refundRequested().forEach(requested -> paths.add(refunded(requested)));
        return paths;
    }

    /** A cobrança desta compra no gateway simulado, com o formato dos ids dele. */
    public static String chargeIdOf(Purchase purchase) {
        return "chg_" + purchase.id().toString().replace("-", "");
    }

    private static Optional<PurchaseChange> recover(Purchase purchase, Instant at) {
        return purchase.recover(RECOVERY_THRESHOLD, purchase.updatedAt().plus(RECOVERY_THRESHOLD).plusSeconds(1));
    }

    public record Path(String name, List<PurchaseChange> changes) {

        public Purchase purchase() {
            return changes.getLast().purchase();
        }

        public List<PurchaseTransition> transitions() {
            return changes.stream().map(PurchaseChange::transition).toList();
        }

        public Path then(String step, BiFunction<Purchase, Instant, PurchaseChange> transition) {
            List<PurchaseChange> next = new ArrayList<>(changes);
            next.add(transition.apply(purchase(), purchase().updatedAt().plusSeconds(1)));
            return new Path(name + " → " + step, List.copyOf(next));
        }

        public Path thenMaybe(String step, BiFunction<Purchase, Instant, Optional<PurchaseChange>> transition) {
            return then(step, (purchase, at) -> transition.apply(purchase, at).orElseThrow(() ->
                    new IllegalStateException(step + " não mudou a compra " + purchase)));
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
