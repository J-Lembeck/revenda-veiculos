package com.revenda.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.MovableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PeriodicSnapshotTests {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-08T12:00:00Z"));
    private final AtomicInteger reads = new AtomicInteger();

    @Test
    void theFirstGetReadsTheSource() {
        PeriodicSnapshot<Integer> snapshot = new PeriodicSnapshot<>(reads::incrementAndGet, 0, Duration.ofSeconds(15),
                clock);

        assertThat(snapshot.get()).isEqualTo(1);
    }

    @Test
    void withinTheIntervalTheLastValueIsKept() {
        PeriodicSnapshot<Integer> snapshot = new PeriodicSnapshot<>(reads::incrementAndGet, 0, Duration.ofSeconds(15),
                clock);
        snapshot.get();

        clock.advance(Duration.ofSeconds(14));

        assertThat(snapshot.get()).isEqualTo(1);
        assertThat(reads).hasValue(1);
    }

    @Test
    void afterTheIntervalTheSourceIsReadAgain() {
        PeriodicSnapshot<Integer> snapshot = new PeriodicSnapshot<>(reads::incrementAndGet, 0, Duration.ofSeconds(15),
                clock);
        snapshot.get();

        clock.advance(Duration.ofSeconds(15));

        assertThat(snapshot.get()).isEqualTo(2);
    }

    @Test
    void aFailedReadKeepsThePreviousValueAndTriesAgainOnlyAfterTheInterval() {
        AtomicInteger calls = new AtomicInteger();
        PeriodicSnapshot<Integer> snapshot = new PeriodicSnapshot<>(() -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("banco fora do ar");
            }
            return calls.get() * 10;
        }, 0, Duration.ofSeconds(15), clock);
        assertThat(snapshot.get()).isEqualTo(10);

        clock.advance(Duration.ofSeconds(15));
        assertThat(snapshot.get()).isEqualTo(10);
        clock.advance(Duration.ofSeconds(1));
        assertThat(snapshot.get()).isEqualTo(10);

        clock.advance(Duration.ofSeconds(15));
        assertThat(snapshot.get()).isEqualTo(30);
    }

    @Test
    void aFailedFirstReadGivesTheInitialValue() {
        PeriodicSnapshot<Integer> snapshot = new PeriodicSnapshot<>(() -> {
            throw new IllegalStateException("banco fora do ar");
        }, -1, Duration.ofSeconds(15), clock);

        assertThat(snapshot.get()).isEqualTo(-1);
    }
}
