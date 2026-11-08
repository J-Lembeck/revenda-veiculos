package com.revenda.sales.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.MovableClock;
import com.revenda.sales.SalesApiTest;
import com.revenda.sales.domain.model.PurchasePaths;
import com.revenda.sales.domain.model.PurchaseStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cada teste tem a própria medida, com relógio próprio: nenhum depende de quem leu o banco antes. */
class PurchaseMetricsTests extends SalesApiTest {

    private final MovableClock time = new MovableClock(Instant.parse("2026-11-08T13:00:00Z"));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void bind() {
        new PurchaseMetrics(jdbc, registry, time).bindTo(registry);
    }

    @Test
    void theGaugeShowsHowManyPurchasesAreInEachStatus() {
        persist(PurchasePaths.paid());
        persist(PurchasePaths.awaiting());
        persist(PurchasePaths.expired());

        for (PurchaseStatus status : PurchaseStatus.values()) {
            assertThat(byStatus(status)).as(status.name()).isEqualTo(inDatabase(status));
        }
    }

    @Test
    void theDatabaseIsReadAtMostOnceEvery15Seconds() {
        double paid = byStatus(PurchaseStatus.PAID);
        persist(PurchasePaths.paid());

        time.advance(Duration.ofSeconds(14));
        assertThat(byStatus(PurchaseStatus.PAID)).isEqualTo(paid);

        time.advance(Duration.ofSeconds(1));
        assertThat(byStatus(PurchaseStatus.PAID)).isEqualTo(paid + 1);
    }

    private double byStatus(PurchaseStatus status) {
        return registry.get(PurchaseMetrics.BY_STATUS).tag("status", status.name()).gauge().value();
    }

    private double inDatabase(PurchaseStatus status) {
        return jdbc.sql("SELECT count(*) FROM purchase WHERE status = :status")
                .param("status", status.name()).query(Long.class).single();
    }
}
