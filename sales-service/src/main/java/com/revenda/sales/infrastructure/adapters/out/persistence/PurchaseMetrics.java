package com.revenda.sales.infrastructure.adapters.out.persistence;

import com.revenda.common.jdbc.Transactions;
import com.revenda.common.observability.PeriodicSnapshot;
import com.revenda.sales.domain.model.PurchaseStatus;
import com.revenda.sales.domain.model.PurchaseTransition;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As compras em números, sem nada de quem comprou: {@code sales.purchase.transitions} soma cada passo da linha do
 * tempo depois do commit, com o status, o pagamento e o motivo (as expirações, os estornos e as reservas recusadas
 * saem daqui); {@code sales.purchases} é quantas compras há em cada status, lido do banco no máximo a cada 15 s.
 */
@Component
class PurchaseMetrics implements MeterBinder {

    static final String TRANSITIONS = "sales.purchase.transitions";
    static final String BY_STATUS = "sales.purchases";
    private static final Duration MAX_AGE = Duration.ofSeconds(15);

    private final PeriodicSnapshot<Map<String, Long>> byStatus;
    private final MeterRegistry meters;

    PurchaseMetrics(JdbcClient jdbc, MeterRegistry meters, Clock clock) {
        this.byStatus = new PeriodicSnapshot<>(() -> countByStatus(jdbc), Map.of(), MAX_AGE, clock);
        this.meters = meters;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (PurchaseStatus status : PurchaseStatus.values()) {
            Gauge.builder(BY_STATUS, byStatus, snapshot -> snapshot.get().getOrDefault(status.name(), 0L))
                    .tag("status", status.name())
                    .description("Compras em cada status")
                    .register(registry);
        }
    }

    void transitioned(PurchaseTransition transition) {
        Transactions.afterCommit(() -> meters.counter(TRANSITIONS,
                        "status", transition.to().name(),
                        "payment_status", transition.paymentStatus().name(),
                        "reason", transition.reason() == null ? "NONE" : transition.reason().name())
                .increment());
    }

    private static Map<String, Long> countByStatus(JdbcClient jdbc) {
        return jdbc.sql("SELECT status, count(*) AS total FROM purchase GROUP BY status")
                .query((row, index) -> Map.entry(row.getString("status"), row.getLong("total")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
