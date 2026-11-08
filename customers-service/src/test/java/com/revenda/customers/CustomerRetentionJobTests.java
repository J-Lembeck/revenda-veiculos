package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.customers.domain.ports.in.AnonymizeDueCustomersUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** O job de retenção: confere cada pedido agendado com o serviço de vendas e anonimiza o que venceu. */
class CustomerRetentionJobTests extends CustomersApiTest {

    private static final Instant FAR = Instant.parse("2031-11-08T15:00:00Z");

    @Autowired
    private AnonymizeDueCustomersUseCase job;

    @Autowired
    private MeterRegistry meters;

    @Test
    void aScheduledProfileStaysWhileTheRetentionLastsAndGoesWhenItEnds() {
        UUID id = scheduled(FAR);

        job.anonymizeDue();
        assertThat(rowOf(id).get("status")).isEqualTo("ACTIVE");

        double before = anonymizations();
        SALES.retains(id, Instant.parse("2025-01-10T09:00:00Z"));
        job.anonymizeDue();

        assertThat(rowOf(id).get("status")).isEqualTo("ANONYMIZED");
        assertThat(rowOf(id).get("full_name")).isNull();
        assertThat(anonymizations()).isEqualTo(before + 1);
        assertThat(jdbc.sql("""
                        SELECT actor_type FROM audit_log WHERE action = 'CUSTOMER_ANONYMIZED' AND entity_id = :id""")
                .param("id", id).query(String.class).single()).isEqualTo("SYSTEM");
    }

    @Test
    void aCancelledSaleEndsTheRetentionAtTheNextRun() {
        UUID id = scheduled(FAR);
        SALES.reset();

        job.anonymizeDue();

        assertThat(rowOf(id).get("status")).isEqualTo("ANONYMIZED");
    }

    @Test
    void profilesWithoutRequestAreNeverTouched() {
        Account account = newAccount();
        UUID id = register(account, customerBody());

        job.anonymizeDue();

        assertThat(rowOf(id).get("status")).isEqualTo("ACTIVE");
        assertThat(SALES.retentionCallsFor(id)).isEmpty();
    }

    @Test
    void withoutAnswerFromSalesTheRunStopsAndChangesNothing() {
        UUID id = scheduled(FAR);
        SALES.retains(id, Instant.parse("2025-01-10T09:00:00Z"));
        SALES.failsWith(503);

        assertThat(job.anonymizeDue()).isZero();
        assertThat(rowOf(id).get("status")).isEqualTo("ACTIVE");
    }

    private UUID scheduled(Instant retainUntil) {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        SALES.retains(id, retainUntil);
        as(account).post(ME + "/deletion", null);
        return id;
    }

    private double anonymizations() {
        Counter counter = meters.find("customers.anonymizations").tag("trigger", "retention").counter();
        return counter == null ? 0 : counter.count();
    }
}
