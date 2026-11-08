package com.revenda.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Period;
import org.junit.jupiter.api.Test;

class PurchaseHistoryTest {

    private static final Period FIVE_YEARS = Period.ofYears(5);
    private static final Instant NOW = Instant.parse("2026-11-08T13:00:00Z");

    @Test
    void withoutSaleNorOpenPurchaseNothingIsRetained() {
        assertThat(PurchaseHistory.NONE.retainUntil(FIVE_YEARS, NOW)).isEmpty();
    }

    @Test
    void aSaleIsRetainedForThePeriodAfterItWasCompleted() {
        PurchaseHistory history = new PurchaseHistory(Instant.parse("2024-02-29T10:15:30Z"), false);

        assertThat(history.retainUntil(FIVE_YEARS, NOW)).contains(Instant.parse("2029-02-28T10:15:30Z"));
    }

    @Test
    void aSaleWhosePeriodIsOverStillAnswersTheDateSoTheCallerSeesItPassed() {
        PurchaseHistory history = new PurchaseHistory(Instant.parse("2020-01-10T09:00:00Z"), false);

        assertThat(history.retainUntil(FIVE_YEARS, NOW)).contains(Instant.parse("2025-01-10T09:00:00Z"));
    }

    @Test
    void anOpenPurchaseCountsAsIfItWereCompletedNow() {
        assertThat(new PurchaseHistory(null, true).retainUntil(FIVE_YEARS, NOW))
                .contains(Instant.parse("2031-11-08T13:00:00Z"));
    }

    @Test
    void theLaterOfTheTwoWins() {
        PurchaseHistory oldSaleAndOpen = new PurchaseHistory(Instant.parse("2025-03-01T00:00:00Z"), true);
        PurchaseHistory futureSaleAndOpen = new PurchaseHistory(Instant.parse("2026-11-08T13:00:01Z"), true);

        assertThat(oldSaleAndOpen.retainUntil(FIVE_YEARS, NOW)).contains(Instant.parse("2031-11-08T13:00:00Z"));
        assertThat(futureSaleAndOpen.retainUntil(FIVE_YEARS, NOW)).contains(Instant.parse("2031-11-08T13:00:01Z"));
    }
}
