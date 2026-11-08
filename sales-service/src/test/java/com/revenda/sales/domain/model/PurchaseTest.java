package com.revenda.sales.domain.model;

import static com.revenda.sales.domain.model.PurchasePaths.PAYMENT_CODE;
import static com.revenda.sales.domain.model.PurchasePaths.PAYMENT_WINDOW;
import static com.revenda.sales.domain.model.PurchasePaths.PRICE;
import static com.revenda.sales.domain.model.PurchasePaths.RECOVERY_THRESHOLD;
import static com.revenda.sales.domain.model.PurchasePaths.REQUESTED_AT;
import static com.revenda.sales.domain.model.PurchasePaths.WRONG_AMOUNT;
import static com.revenda.sales.domain.model.PurchasePaths.chargeIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PurchaseTest {

    private static final String OTHER_CHARGE = "chg_" + "0".repeat(32);

    @Test
    void aRequestedPurchaseIsPendingWithoutPriceNorPayment() {
        UUID id = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID vehicle = UUID.randomUUID();
        String subject = UUID.randomUUID().toString();

        PurchaseChange change = Purchase.request(id, customer, subject, vehicle, REQUESTED_AT);

        Purchase purchase = change.purchase();
        assertThat(purchase.id()).isEqualTo(id);
        assertThat(purchase.customerId()).isEqualTo(customer);
        assertThat(purchase.customerSubject()).isEqualTo(subject);
        assertThat(purchase.vehicleId()).isEqualTo(vehicle);
        assertThat(purchase.status()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(purchase.paymentStatus()).isEqualTo(PaymentStatus.NONE);
        assertThat(purchase.price()).isNull();
        assertThat(purchase.closedReason()).isNull();
        assertThat(purchase.version()).isZero();
        assertThat(purchase.createdAt()).isEqualTo(REQUESTED_AT).isEqualTo(purchase.updatedAt());
        assertThat(change.transition()).isEqualTo(new PurchaseTransition(id, null, PurchaseStatus.PENDING,
                PaymentStatus.NONE, TransitionReason.PURCHASE_REQUESTED, REQUESTED_AT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void aPurchaseBelongsToAnAccount(String subject) {
        assertThatThrownBy(() -> Purchase.request(UUID.randomUUID(), UUID.randomUUID(), subject, UUID.randomUUID(),
                REQUESTED_AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theReservationFixesThePriceTheVehicleHadThen() {
        Purchase pending = PurchasePaths.pending().purchase();
        Instant at = REQUESTED_AT.plusSeconds(1);

        PurchaseChange change = pending.reserve(PRICE, at);

        assertMoved(change, pending, PurchaseStatus.RESERVED, PaymentStatus.NONE, TransitionReason.VEHICLE_RESERVED, at);
        assertThat(change.purchase().price()).isEqualTo(PRICE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void theReservedPriceIsPositive(long cents) {
        Purchase pending = PurchasePaths.pending().purchase();

        assertThatThrownBy(() -> pending.reserve(Money.ofCents(cents), REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePriceStaysTheOneOfTheReservationUntilThePickup() {
        PurchasePaths.Path completed = PurchasePaths.completed();

        assertThat(completed.changes().stream().skip(1).map(change -> change.purchase().price())).containsOnly(PRICE);
    }

    @Test
    void thePaymentIsComparedWithThePriceOfTheReservationNotAnotherOne() {
        Purchase reservedCheaper = PurchasePaths.pending().purchase().reserve(WRONG_AMOUNT, REQUESTED_AT).purchase();
        Purchase awaiting = reservedCheaper.awaitPayment(chargeIdOf(reservedCheaper), PAYMENT_CODE,
                REQUESTED_AT.plus(PAYMENT_WINDOW), REQUESTED_AT).purchase();

        Purchase paid = awaiting.recordPayment(awaiting.chargeId(), WRONG_AMOUNT, REQUESTED_AT, REQUESTED_AT)
                .orElseThrow().purchase();
        Purchase mismatched = awaiting.recordPayment(awaiting.chargeId(), PRICE, REQUESTED_AT, REQUESTED_AT)
                .orElseThrow().purchase();

        assertThat(paid.status()).isEqualTo(PurchaseStatus.PAID);
        assertThat(mismatched.closedReason()).isEqualTo(ClosedReason.PAYMENT_MISMATCH);
    }

    @Test
    void aRefusedReservationRejectsThePurchaseWithoutPrice() {
        Purchase pending = PurchasePaths.pending().purchase();

        PurchaseChange change = pending.reject(REQUESTED_AT.plusSeconds(1));

        assertClosed(change, pending, PurchaseStatus.REJECTED, PaymentStatus.NONE, ClosedReason.VEHICLE_UNAVAILABLE);
        assertThat(change.purchase().price()).isNull();
    }

    @Test
    void aReservationWithoutAnswerFailsThePurchaseWithoutPrice() {
        Purchase pending = PurchasePaths.pending().purchase();

        PurchaseChange change = pending.failReservation(REQUESTED_AT.plusSeconds(1));

        assertClosed(change, pending, PurchaseStatus.FAILED, PaymentStatus.NONE, ClosedReason.RESERVATION_FAILED);
        assertThat(change.purchase().price()).isNull();
    }

    @Test
    void theIssuedChargeTakesThePurchaseToAwaitingPayment() {
        Purchase reserved = PurchasePaths.reserved().purchase();
        Instant at = reserved.updatedAt().plusSeconds(1);
        Instant due = at.plus(PAYMENT_WINDOW);

        PurchaseChange change = reserved.awaitPayment("chg_1", PAYMENT_CODE, due, at);

        assertMoved(change, reserved, PurchaseStatus.AWAITING_PAYMENT, PaymentStatus.PENDING,
                TransitionReason.CHARGE_ISSUED, at);
        assertThat(change.purchase().chargeId()).isEqualTo("chg_1");
        assertThat(change.purchase().paymentCode()).isEqualTo(PAYMENT_CODE);
        assertThat(change.purchase().paymentDueAt()).isEqualTo(due);
    }

    @Test
    void aChargeThatWasNotIssuedFailsThePurchase() {
        Purchase reserved = PurchasePaths.reserved().purchase();

        PurchaseChange change = reserved.failCharge(reserved.updatedAt().plusSeconds(1));

        assertClosed(change, reserved, PurchaseStatus.FAILED, PaymentStatus.NONE, ClosedReason.CHARGE_FAILED);
        assertThat(change.purchase().price()).isEqualTo(PRICE);
    }

    static Stream<PurchasePaths.Path> notPending() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().status() != PurchaseStatus.PENDING);
    }

    @ParameterizedTest
    @MethodSource("notPending")
    void onlyAPendingPurchaseGoesThroughTheReservation(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();
        Instant at = purchase.updatedAt().plusSeconds(1);

        assertOutOfOrder(() -> purchase.reserve(PRICE, at));
        assertOutOfOrder(() -> purchase.reject(at));
        assertOutOfOrder(() -> purchase.failReservation(at));
    }

    static Stream<PurchasePaths.Path> notReserved() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().status() != PurchaseStatus.RESERVED);
    }

    @ParameterizedTest
    @MethodSource("notReserved")
    void onlyAReservedPurchaseGoesThroughTheCharge(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();
        Instant at = purchase.updatedAt().plusSeconds(1);

        assertOutOfOrder(() -> purchase.awaitPayment("chg_1", PAYMENT_CODE, at.plus(PAYMENT_WINDOW), at));
        assertOutOfOrder(() -> purchase.failCharge(at));
    }

    @Test
    void givingUpBeforePayingCancelsTheCharge() {
        Purchase awaiting = PurchasePaths.awaiting().purchase();

        PurchaseChange change = awaiting.cancel(awaiting.updatedAt().plusSeconds(1));

        assertClosed(change, awaiting, PurchaseStatus.CANCELLED, PaymentStatus.CANCELLED, ClosedReason.CUSTOMER_GAVE_UP);
    }

    @Test
    void givingUpAfterPayingAsksForTheRefund() {
        Purchase paid = PurchasePaths.paid().purchase();

        PurchaseChange change = paid.cancel(paid.updatedAt().plusSeconds(1));

        assertClosed(change, paid, PurchaseStatus.CANCELLED, PaymentStatus.REFUND_REQUESTED,
                ClosedReason.CUSTOMER_GAVE_UP);
        assertThat(change.purchase().paidAmount()).isEqualTo(PRICE);
    }

    static Stream<PurchasePaths.Path> inFlight() {
        return Stream.of(PurchasePaths.pending(), PurchasePaths.reserved());
    }

    @ParameterizedTest
    @MethodSource("inFlight")
    void givingUpDuringTheReservationAsksToTryAgain(PurchasePaths.Path path) {
        assertRefused(() -> path.purchase().cancel(REQUESTED_AT), SalesError.PURCHASE_IN_PROGRESS);
    }

    static Stream<PurchasePaths.Path> closedOrCompleted() {
        return PurchasePaths.all().stream().filter(path -> !path.purchase().status().isInFlight()
                && path.purchase().status() != PurchaseStatus.AWAITING_PAYMENT
                && path.purchase().status() != PurchaseStatus.PAID);
    }

    @ParameterizedTest
    @MethodSource("closedOrCompleted")
    void aClosedOrCompletedPurchaseIsNotCancelled(PurchasePaths.Path path) {
        assertRefused(() -> path.purchase().cancel(REQUESTED_AT), SalesError.PURCHASE_NOT_CANCELLABLE);
    }

    @Test
    void thePickupCompletesThePaidPurchase() {
        Purchase paid = PurchasePaths.paid().purchase();
        Instant at = paid.updatedAt().plusSeconds(1);

        PurchaseChange change = paid.confirmPickup(at);

        assertMoved(change, paid, PurchaseStatus.COMPLETED, PaymentStatus.PAID, TransitionReason.PICKUP_CONFIRMED, at);
        assertThat(change.purchase().completedAt()).isEqualTo(at);
        assertThat(change.purchase().closedReason()).isNull();
    }

    @Test
    void theDocumentIsRecordedOnceOnTheCompletedPurchase() {
        Purchase completed = PurchasePaths.completed().purchase();
        Instant at = completed.updatedAt().plusSeconds(1);
        String key = TransferDocument.keyOf(completed);

        PurchaseChange change = completed.recordDocument(key, at).orElseThrow();

        assertMoved(change, completed, PurchaseStatus.COMPLETED, PaymentStatus.PAID, TransitionReason.DOCUMENT_ISSUED,
                at);
        assertThat(change.purchase().documentKey()).isEqualTo(key).doesNotContain(completed.customerSubject())
                .contains(completed.id().toString());
        assertThat(change.purchase().completedAt()).isEqualTo(completed.completedAt());
        assertThat(change.purchase().recordDocument(key, at.plusSeconds(1))).as("o comando repetido").isEmpty();
    }

    static Stream<PurchasePaths.Path> notCompleted() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().status() != PurchaseStatus.COMPLETED);
    }

    @ParameterizedTest
    @MethodSource("notCompleted")
    void onlyTheCompletedPurchaseGetsADocument(PurchasePaths.Path path) {
        assertThatThrownBy(() -> path.purchase().recordDocument("purchases/x/transfer-document.pdf", REQUESTED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theDocumentNeedsAKey() {
        Purchase completed = PurchasePaths.completed().purchase();

        assertThatThrownBy(() -> completed.recordDocument(" ", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDocumentIsOnlyOfTheCompletedPurchaseAndItsKeyCarriesNothingOfTheBuyer() {
        Purchase paid = PurchasePaths.paid().purchase();

        assertThatThrownBy(() -> new TransferDocument(paid, null, null, REQUESTED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThat(TransferDocument.keyOf(paid)).isEqualTo("purchases/" + paid.id() + "/transfer-document.pdf");
    }

    static Stream<PurchasePaths.Path> notPaid() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().status() != PurchaseStatus.PAID);
    }

    @ParameterizedTest
    @MethodSource("notPaid")
    void onlyAPaidPurchaseIsPickedUp(PurchasePaths.Path path) {
        assertRefused(() -> path.purchase().confirmPickup(REQUESTED_AT), SalesError.PURCHASE_NOT_PAID);
    }

    @Test
    void thePaymentOfThePriceConfirmsThePurchase() {
        Purchase awaiting = PurchasePaths.awaiting().purchase();
        Instant paidAt = awaiting.updatedAt().plusSeconds(1);
        Instant at = paidAt.plusSeconds(1);

        PurchaseChange change = awaiting.recordPayment(awaiting.chargeId(), PRICE, paidAt, at).orElseThrow();

        assertMoved(change, awaiting, PurchaseStatus.PAID, PaymentStatus.PAID, TransitionReason.PAYMENT_CONFIRMED, at);
        assertThat(change.purchase().paidAt()).isEqualTo(paidAt);
        assertThat(change.purchase().paidAmount()).isEqualTo(PRICE);
    }

    @Test
    void aPaymentAfterTheDueTimeButBeforeTheSweepStillConfirms() {
        Purchase awaiting = PurchasePaths.awaiting().purchase();
        Instant late = awaiting.paymentDueAt().plusSeconds(10);

        Purchase paid = awaiting.recordPayment(awaiting.chargeId(), PRICE, late, late).orElseThrow().purchase();

        assertThat(paid.status()).isEqualTo(PurchaseStatus.PAID);
    }

    @Test
    void aPaymentOfAnotherAmountFailsThePurchaseAndAsksForTheRefund() {
        Purchase awaiting = PurchasePaths.awaiting().purchase();
        Instant at = awaiting.updatedAt().plusSeconds(1);

        PurchaseChange change = awaiting.recordPayment(awaiting.chargeId(), WRONG_AMOUNT, at, at).orElseThrow();

        assertClosed(change, awaiting, PurchaseStatus.FAILED, PaymentStatus.REFUND_REQUESTED,
                ClosedReason.PAYMENT_MISMATCH);
        assertThat(change.purchase().paidAmount()).isEqualTo(WRONG_AMOUNT);
        assertThat(change.purchase().price()).isEqualTo(PRICE);
    }

    static Stream<PurchasePaths.Path> closedUnpaid() {
        return PurchasePaths.closedUnpaid().stream();
    }

    @ParameterizedTest
    @MethodSource("closedUnpaid")
    void aLatePaymentAsksForTheRefundAndThePurchaseDoesNotRevive(PurchasePaths.Path path) {
        Purchase closed = path.purchase();
        Instant at = closed.updatedAt().plusSeconds(1);

        PurchaseChange change = closed.recordPayment(chargeIdOf(closed), PRICE, at, at).orElseThrow();

        assertMoved(change, closed, closed.status(), PaymentStatus.REFUND_REQUESTED, TransitionReason.LATE_PAYMENT, at);
        assertThat(change.transition().from()).isEqualTo(change.transition().to());
        assertThat(change.purchase().closedReason()).isEqualTo(closed.closedReason());
        assertThat(change.purchase().chargeId()).isEqualTo(chargeIdOf(closed));
        assertThat(change.purchase().paidAmount()).isEqualTo(PRICE);
    }

    @ParameterizedTest
    @MethodSource("closedUnpaid")
    void aLatePaymentOfAnotherAmountIsRefundedTheSameWay(PurchasePaths.Path path) {
        Purchase closed = path.purchase();

        Purchase refunding = closed.recordPayment(chargeIdOf(closed), WRONG_AMOUNT, REQUESTED_AT, REQUESTED_AT)
                .orElseThrow().purchase();

        assertThat(refunding.status()).isEqualTo(closed.status());
        assertThat(refunding.paymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUESTED);
        assertThat(refunding.paidAmount()).isEqualTo(WRONG_AMOUNT);
    }

    @ParameterizedTest
    @MethodSource("inFlight")
    void aPaymentBeforeTheChargeIsRecordedComesBackLater(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();

        assertRefused(() -> purchase.recordPayment(chargeIdOf(purchase), PRICE, REQUESTED_AT, REQUESTED_AT),
                SalesError.PAYMENT_EVENT_NOT_YET_APPLICABLE);
    }

    static Stream<PurchasePaths.Path> paymentReceived() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().paymentStatus().isReceived());
    }

    @ParameterizedTest
    @MethodSource("paymentReceived")
    void aPaymentAlreadyRecordedChangesNothing(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();

        assertThat(purchase.recordPayment(purchase.chargeId(), PRICE, REQUESTED_AT, REQUESTED_AT)).isEmpty();
    }

    static Stream<PurchasePaths.Path> withCharge() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().chargeId() != null);
    }

    @ParameterizedTest
    @MethodSource("withCharge")
    void anEventOfAnotherChargeContradictsThePurchase(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();

        assertRefused(() -> purchase.recordPayment(OTHER_CHARGE, PRICE, REQUESTED_AT, REQUESTED_AT),
                SalesError.PAYMENT_EVENT_CONTRADICTORY);
        assertRefused(() -> purchase.recordRefund(OTHER_CHARGE, REQUESTED_AT), SalesError.PAYMENT_EVENT_CONTRADICTORY);
    }

    static Stream<PurchasePaths.Path> refundRequested() {
        return PurchasePaths.refundRequested().stream();
    }

    @ParameterizedTest
    @MethodSource("refundRequested")
    void theConfirmedRefundOnlyChangesThePayment(PurchasePaths.Path path) {
        Purchase refunding = path.purchase();
        Instant at = refunding.updatedAt().plusSeconds(1);

        PurchaseChange change = refunding.recordRefund(refunding.chargeId(), at).orElseThrow();

        assertMoved(change, refunding, refunding.status(), PaymentStatus.REFUNDED, TransitionReason.REFUND_CONFIRMED,
                at);
        assertThat(change.purchase().closedReason()).isEqualTo(refunding.closedReason());
        assertThat(change.purchase().paidAmount()).isEqualTo(refunding.paidAmount());
    }

    @ParameterizedTest
    @MethodSource("refundRequested")
    void aRepeatedRefundChangesNothing(PurchasePaths.Path path) {
        Purchase refunded = PurchasePaths.refunded(path).purchase();

        assertThat(refunded.recordRefund(refunded.chargeId(), refunded.updatedAt().plusSeconds(1))).isEmpty();
    }

    static Stream<PurchasePaths.Path> withoutRefundRequested() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().paymentStatus() != PaymentStatus.REFUND_REQUESTED
                && path.purchase().paymentStatus() != PaymentStatus.REFUNDED);
    }

    @ParameterizedTest
    @MethodSource("withoutRefundRequested")
    void aRefundNobodyAskedForContradictsThePurchase(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();

        assertRefused(() -> purchase.recordRefund(chargeIdOf(purchase), REQUESTED_AT),
                SalesError.PAYMENT_EVENT_CONTRADICTORY);
    }

    @Test
    void anEventWithoutChargeIsRefused() {
        Purchase awaiting = PurchasePaths.awaiting().purchase();

        assertThatThrownBy(() -> awaiting.recordPayment(" ", PRICE, REQUESTED_AT, REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> awaiting.recordPayment(awaiting.chargeId(), Money.ZERO, REQUESTED_AT, REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDueTimeExpiresThePurchaseAndCancelsTheCharge() {
        Purchase awaiting = PurchasePaths.awaiting().purchase();
        Instant at = awaiting.paymentDueAt().plusMillis(1);

        PurchaseChange change = awaiting.expire(at).orElseThrow();

        assertClosed(change, awaiting, PurchaseStatus.EXPIRED, PaymentStatus.CANCELLED, ClosedReason.PAYMENT_EXPIRED);
        assertThat(change.transition().occurredAt()).isEqualTo(at);
    }

    @Test
    void thePurchaseDoesNotExpireUntilTheDueTimeHasPassed() {
        Purchase awaiting = PurchasePaths.awaiting().purchase();

        assertThat(awaiting.expire(awaiting.paymentDueAt())).isEmpty();
        assertThat(awaiting.expire(awaiting.paymentDueAt().minusSeconds(1))).isEmpty();
    }

    static Stream<PurchasePaths.Path> notAwaiting() {
        return PurchasePaths.all().stream().filter(path -> path.purchase().status() != PurchaseStatus.AWAITING_PAYMENT);
    }

    @ParameterizedTest
    @MethodSource("notAwaiting")
    void theSweepSkipsAPurchaseThatLeftAwaitingPayment(PurchasePaths.Path path) {
        assertThat(path.purchase().expire(REQUESTED_AT.plus(PAYMENT_WINDOW).plusSeconds(3_600))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("inFlight")
    void aPurchaseStuckInTheMiddleFailsByRecovery(PurchasePaths.Path path) {
        Purchase stuck = path.purchase();
        Instant at = stuck.updatedAt().plus(RECOVERY_THRESHOLD).plusMillis(1);

        PurchaseChange change = stuck.recover(RECOVERY_THRESHOLD, at).orElseThrow();

        assertClosed(change, stuck, PurchaseStatus.FAILED, PaymentStatus.NONE, ClosedReason.RECOVERY_TIMEOUT);
        assertThat(change.purchase().price()).isEqualTo(stuck.price());
    }

    @ParameterizedTest
    @MethodSource("inFlight")
    void aPurchaseWithinTheThresholdIsNotRecovered(PurchasePaths.Path path) {
        Purchase moving = path.purchase();

        assertThat(moving.recover(RECOVERY_THRESHOLD, moving.updatedAt().plus(RECOVERY_THRESHOLD))).isEmpty();
    }

    static Stream<PurchasePaths.Path> notInFlight() {
        return PurchasePaths.all().stream().filter(path -> !path.purchase().status().isInFlight());
    }

    @ParameterizedTest
    @MethodSource("notInFlight")
    void theRecoverySkipsAPurchaseThatMoved(PurchasePaths.Path path) {
        assertThat(path.purchase().recover(RECOVERY_THRESHOLD, REQUESTED_AT.plusSeconds(86_400))).isEmpty();
    }

    static Stream<PurchasePaths.Path> allPaths() {
        return PurchasePaths.all().stream();
    }

    @ParameterizedTest
    @MethodSource("allPaths")
    void everyStepIsTheNextVersionAndItsTransitionLinksTheTwo(PurchasePaths.Path path) {
        List<PurchaseChange> changes = path.changes();
        for (int step = 1; step < changes.size(); step++) {
            Purchase before = changes.get(step - 1).purchase();
            PurchaseChange change = changes.get(step);
            assertThat(change.purchase().version()).isEqualTo(before.version() + 1);
            assertThat(change.purchase().createdAt()).isEqualTo(before.createdAt());
            assertThat(change.transition().purchaseId()).isEqualTo(before.id());
            assertThat(change.transition().from()).isEqualTo(before.status());
            assertThat(change.transition().to()).isEqualTo(change.purchase().status());
            assertThat(change.transition().paymentStatus()).isEqualTo(change.purchase().paymentStatus());
            assertThat(change.transition().occurredAt()).isEqualTo(change.purchase().updatedAt());
        }
    }

    @ParameterizedTest
    @MethodSource("allPaths")
    void theClosedReasonIsOnlyOnAClosedPurchaseAndNamesItsTransition(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();
        boolean closed = switch (purchase.status()) {
            case REJECTED, FAILED, EXPIRED, CANCELLED -> true;
            default -> false;
        };

        assertThat(purchase.closedReason() != null).isEqualTo(closed);
        if (closed) {
            assertThat(path.transitions()).extracting(PurchaseTransition::reason)
                    .contains(TransitionReason.closing(purchase.closedReason()));
        }
    }

    @Test
    void everyClosedReasonHasTheTransitionReasonOfTheSameName() {
        assertThat(Arrays.stream(ClosedReason.values()).map(TransitionReason::closing).map(Enum::name))
                .containsExactlyElementsOf(Arrays.stream(ClosedReason.values()).map(Enum::name).toList());
    }

    @ParameterizedTest
    @MethodSource("allPaths")
    void theTextOfAPurchaseShowsNothingAboutTheCustomer(PurchasePaths.Path path) {
        Purchase purchase = path.purchase();

        assertThat(purchase.toString())
                .contains(purchase.id().toString(), purchase.status().name())
                .doesNotContain(purchase.customerSubject(), purchase.customerId().toString(), PAYMENT_CODE);
        assertThat(purchase.toBuilder().toString())
                .doesNotContain(purchase.customerSubject(), purchase.customerId().toString(), PAYMENT_CODE);
    }

    private static void assertMoved(PurchaseChange change, Purchase before, PurchaseStatus status,
            PaymentStatus paymentStatus, TransitionReason reason, Instant at) {
        Purchase after = change.purchase();
        assertThat(after.status()).isEqualTo(status);
        assertThat(after.paymentStatus()).isEqualTo(paymentStatus);
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(after.updatedAt()).isEqualTo(at);
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.customerSubject()).isEqualTo(before.customerSubject());
        assertThat(change.transition()).isEqualTo(new PurchaseTransition(before.id(), before.status(), status,
                paymentStatus, reason, at));
    }

    private static void assertClosed(PurchaseChange change, Purchase before, PurchaseStatus status,
            PaymentStatus paymentStatus, ClosedReason reason) {
        assertMoved(change, before, status, paymentStatus, TransitionReason.closing(reason),
                change.purchase().updatedAt());
        assertThat(change.purchase().closedReason()).isEqualTo(reason);
    }

    private static void assertOutOfOrder(Runnable step) {
        assertThatThrownBy(step::run).isInstanceOf(IllegalStateException.class);
    }

    private static void assertRefused(Runnable action, SalesError error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                refused -> assertThat(refused.getError()).isEqualTo(error));
    }
}
