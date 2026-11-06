package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchasePaths;
import com.revenda.sales.domain.ports.in.SweepPurchasesUseCase;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * UC-10: a varredura dos prazos — pagamento não feito, pagamento atrasado, queda no meio do caminho síncrono e a corrida
 * entre o prazo e o pagamento (06 §4 e §10).
 */
class PurchaseSweepTests extends SalesApiTest {

    @Autowired
    private SweepPurchasesUseCase sweep;

    @Autowired
    private LockPurchasePort locks;

    @Test
    void anOverduePurchaseExpiresCancelsTheChargeAndReleasesTheVehicle() {
        Purchase overdue = persist(PurchasePaths.awaiting());

        sweep.expireOverdue();

        assertThat(purchaseRow(overdue.id())).containsEntry("status", "EXPIRED")
                .containsEntry("closed_reason", "PAYMENT_EXPIRED").containsEntry("payment_status", "CANCELLED");
        assertThat(timelineOf(overdue.id())).last().isEqualTo("EXPIRED/CANCELLED/PAYMENT_EXPIRED");
        assertThat(lastActorOf(overdue.id())).isEqualTo("SYSTEM");
        assertThat(outboxOf(overdue.id())).containsExactly("PAYMENT_GATEWAY CancelCharge",
                "VEHICLE_COMMANDS ReleaseVehicle");
        assertThat(json.readTree((String) outboxRowsOf(overdue.id()).getFirst().get("payload")).get("chargeId")
                .asString()).isEqualTo(overdue.chargeId());
    }

    @Test
    void aPurchaseStillWithinItsWindowIsLeftAlone() {
        UUID id = awaitingPurchase(newCustomer());

        sweep.expireOverdue();
        sweep.recoverStalled();

        assertThat(statusOf(id)).isEqualTo("AWAITING_PAYMENT");
        assertThat(outboxOf(id)).isEmpty();
    }

    @Test
    void aPaymentAfterTheDeadlineIsRefundedAndThePurchaseStaysExpired() {
        Purchase overdue = persist(PurchasePaths.awaiting());
        sweep.expireOverdue();

        webhook(paymentEvent(newEventId(), "CHARGE_PAID", overdue.chargeId(), overdue.id(), 8_990_000));

        assertThat(purchaseRow(overdue.id())).containsEntry("status", "EXPIRED")
                .containsEntry("payment_status", "REFUND_REQUESTED");
        assertThat(outboxOf(overdue.id())).last().isEqualTo("PAYMENT_GATEWAY RefundCharge");
    }

    @Test
    void aPurchaseThatDiedBetweenT1AndT2FailsAndReleasesTheVehicle() {
        Purchase stalled = persist(PurchasePaths.pending());

        sweep.recoverStalled();

        assertThat(purchaseRow(stalled.id())).containsEntry("status", "FAILED")
                .containsEntry("closed_reason", "RECOVERY_TIMEOUT");
        assertThat(timelineOf(stalled.id())).last().isEqualTo("FAILED/NONE/RECOVERY_TIMEOUT");
        assertThat(outboxOf(stalled.id())).containsExactly("VEHICLE_COMMANDS ReleaseVehicle",
                "PAYMENT_GATEWAY CancelCharge");
    }

    @Test
    void aPurchaseThatDiedReservedFailsReleasingAndCancellingByThePurchase() {
        Purchase stalled = persist(PurchasePaths.reserved());

        sweep.recoverStalled();

        assertThat(purchaseRow(stalled.id())).containsEntry("status", "FAILED")
                .containsEntry("closed_reason", "RECOVERY_TIMEOUT");
        assertThat(outboxRowsOf(stalled.id()).get(1)).containsEntry("type", "CancelCharge")
                .containsEntry("group_key", stalled.id().toString());
        assertThat(json.readTree((String) outboxRowsOf(stalled.id()).get(1).get("payload")).get("chargeId").isNull())
                .as("sem a cobrança, o gateway procura pela compra").isTrue();
    }

    @Test
    void aPurchaseInTheMiddleOfItsSynchronousPathIsNotRecovered() {
        String subject = newCustomer();
        UUID vehicle = UUID.randomUUID();
        NEIGHBORS.vehicleAnswers(vehicle, FakeNeighbors.Reply.price(8_990_000).after(Duration.ofMillis(300)));
        Thread buying = Thread.ofPlatform().start(() -> buy(subject, vehicle));

        Awaitility.await().atMost(PATIENCE).until(() -> jdbc.sql(
                "SELECT count(*) FROM purchase WHERE vehicle_id = :vehicle").param("vehicle", vehicle)
                .query(Integer.class).single() == 1);
        sweep.recoverStalled();
        joinQuietly(buying);

        UUID id = jdbc.sql("SELECT id FROM purchase WHERE vehicle_id = :vehicle").param("vehicle", vehicle)
                .query(UUID.class).single();
        assertThat(statusOf(id)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void aPurchaseLockedByAnotherTransactionIsSkipped() throws Exception {
        Purchase overdue = persist(PurchasePaths.awaiting());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = Thread.ofPlatform().start(() -> ActorContextHolder.callAs(ActorContext.system(), () ->
                transaction.execute(status -> {
                    locks.lock(overdue.id());
                    locked.countDown();
                    awaitQuietly(release);
                    return null;
                })));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        sweep.expireOverdue();
        String whileLocked = statusOf(overdue.id());
        release.countDown();
        holder.join(10_000);
        sweep.expireOverdue();

        assertThat(whileLocked).isEqualTo("AWAITING_PAYMENT");
        assertThat(statusOf(overdue.id())).isEqualTo("EXPIRED");
    }

    @Test
    void whenThePaymentWinsTheSweepSkipsThePurchase() {
        Purchase overdue = persist(PurchasePaths.awaiting());

        webhook(paymentEvent(newEventId(), "CHARGE_PAID", overdue.chargeId(), overdue.id(), 8_990_000));
        sweep.expireOverdue();

        assertThat(purchaseRow(overdue.id())).containsEntry("status", "PAID").containsEntry("payment_status", "PAID");
        assertThat(outboxOf(overdue.id())).containsExactly("VEHICLE_COMMANDS MarkVehicleSold");
    }

    @RepeatedTest(5)
    void theDeadlineAndThePaymentAtOnceEndPaidOrExpiredWithTheRefund() throws Exception {
        Purchase overdue = persist(PurchasePaths.awaiting());

        List<Object> answers = atOnce(
                () -> webhook(paymentEvent(newEventId(), "CHARGE_PAID", overdue.chargeId(), overdue.id(), 8_990_000))
                        .getStatusCode(),
                () -> sweep.expireOverdue());

        assertThat(answers.getFirst()).isEqualTo(HttpStatus.OK);
        String status = statusOf(overdue.id());
        assertThat(status).isIn("PAID", "EXPIRED");
        if (status.equals("PAID")) {
            assertThat(purchaseRow(overdue.id())).containsEntry("payment_status", "PAID");
            assertThat(outboxOf(overdue.id())).containsExactly("VEHICLE_COMMANDS MarkVehicleSold");
        } else {
            assertThat(purchaseRow(overdue.id())).containsEntry("payment_status", "REFUND_REQUESTED");
            assertThat(outboxOf(overdue.id())).containsExactly("PAYMENT_GATEWAY CancelCharge",
                    "VEHICLE_COMMANDS ReleaseVehicle", "PAYMENT_GATEWAY RefundCharge");
        }
    }

    private String lastActorOf(UUID id) {
        return jdbc.sql("SELECT actor_type FROM purchase_transition WHERE purchase_id = :id ORDER BY id DESC LIMIT 1")
                .param("id", id).query(String.class).single();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(10_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
