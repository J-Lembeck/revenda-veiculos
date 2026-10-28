package com.revenda.payments.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChargeTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private static final Money AMOUNT = Money.ofCents(4_590_000);
    private static final Payer PAYER = new Payer("Pagador Sintético", "52998224725",
            new PayerAddress("01310000", "Rua das Amostras", "100", null, "Centro", "São Paulo", "SP"));

    @Test
    void aNewChargeIsPendingWithThePixCodeAndNothingPaid() {
        Charge charge = open();

        assertThat(charge.status()).isEqualTo(ChargeStatus.PENDING);
        assertThat(charge.pixCode()).isEqualTo(PixCode.of("chg_1", AMOUNT));
        assertThat(charge.paidAmount()).isNull();
        assertThat(charge.createdAt()).isEqualTo(NOW).isEqualTo(charge.updatedAt());
    }

    @Test
    void theDueDateIsAfterNow() {
        assertThatThrownBy(() -> Charge.open("chg_1", "ref-1", AMOUNT, NOW, PAYER, NOW))
                .isInstanceOfSatisfying(BusinessException.class, refused ->
                        assertThat(refused.getFields()).singleElement()
                                .satisfies(field -> assertThat(field.field() + " " + field.code())
                                        .isEqualTo("dueAt DATE_INVALID")));
    }

    @Test
    void onlyAPendingChargeIsCancelledAndCancellingAgainChangesNothing() {
        Charge cancelled = open().cancel(LATER);
        Charge paid = open().pay(AMOUNT, NOW);

        assertThat(cancelled.status()).isEqualTo(ChargeStatus.CANCELLED);
        assertThat(cancelled.updatedAt()).isEqualTo(LATER);
        assertThat(cancelled.cancel(LATER.plusSeconds(1))).isSameAs(cancelled);
        assertThat(paid.cancel(LATER)).isSameAs(paid);
    }

    @Test
    void onlyAPaidChargeIsRefundedAndRefundingAgainChangesNothing() {
        Charge pending = open();
        Charge refunded = pending.pay(AMOUNT, NOW).refund(LATER);

        assertThat(refunded.status()).isEqualTo(ChargeStatus.REFUNDED);
        assertThat(refunded.paidAmount()).isEqualTo(AMOUNT);
        assertThat(refunded.refund(LATER)).isSameAs(refunded);
        assertThat(pending.refund(LATER)).isSameAs(pending);
        assertThat(pending.cancel(NOW).refund(LATER).status()).isEqualTo(ChargeStatus.CANCELLED);
    }

    @Test
    void aPendingOrCancelledChargeIsPaidButAPaidOrRefundedOneIsNot() {
        Charge late = open().cancel(NOW).pay(AMOUNT, LATER);
        Charge paid = open().pay(AMOUNT, LATER);

        assertThat(late.status()).isEqualTo(ChargeStatus.PAID);
        assertThat(late.paidAmount()).isEqualTo(AMOUNT);
        assertThat(late.updatedAt()).isEqualTo(LATER);
        for (Charge closed : new Charge[] {paid, paid.refund(LATER)}) {
            assertThatThrownBy(() -> closed.pay(AMOUNT, LATER))
                    .isInstanceOfSatisfying(BusinessException.class, refused ->
                            assertThat(refused.getCode()).isEqualTo("CHARGE_NOT_PAYABLE"));
        }
    }

    @Test
    void theWrongAmountIsOneRealLessButNeverLessThanACent() {
        assertThat(PaymentOutcome.PAID.paidFor(AMOUNT)).isEqualTo(AMOUNT);
        assertThat(PaymentOutcome.PAID_WRONG_AMOUNT.paidFor(AMOUNT)).isEqualTo(Money.ofCents(4_589_900));
        assertThat(PaymentOutcome.PAID_WRONG_AMOUNT.paidFor(Money.ofCents(100))).isEqualTo(Money.ofCents(1));
        assertThat(PaymentOutcome.PAID_WRONG_AMOUNT.paidFor(Money.ofCents(1))).isEqualTo(Money.ofCents(1));
    }

    @Test
    void aRepetitionIsTheSameAmountAndThePayerNeverShows() {
        Charge charge = open();
        Charge sameAmount = Charge.open("chg_2", "ref-1", AMOUNT, NOW.plus(Duration.ofHours(1)), PAYER, NOW);
        Charge otherAmount = Charge.open("chg_3", "ref-1", Money.ofCents(1), NOW.plus(Duration.ofHours(1)), PAYER, NOW);

        assertThat(charge.repeats(sameAmount)).isTrue();
        assertThat(charge.repeats(otherAmount)).isFalse();
        assertThat(charge.toString() + PAYER + PAYER.address())
                .doesNotContain("Pagador", "52998224725", "Rua das Amostras", "01310000");
    }

    private static Charge open() {
        return Charge.open("chg_1", "ref-1", AMOUNT, NOW.plus(Duration.ofMinutes(30)), PAYER, NOW);
    }
}
