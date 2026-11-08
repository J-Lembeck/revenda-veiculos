package com.revenda.common.outbox;

import com.revenda.common.observability.PeriodicSnapshot;
import com.revenda.common.time.SqlTime;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * O outbox de cada destino em duas medidas, as dos alarmes: quantas mensagens desistiram ({@code FAILED}) e há
 * quanto tempo espera a mais antiga ainda não enviada. Lidas do banco no máximo uma vez a cada 15 segundos.
 */
class OutboxMetrics implements MeterBinder {

    static final String FAILED = "outbox.messages.failed";
    static final String OLDEST_AGE = "outbox.messages.oldest.age";
    static final Duration MAX_AGE = Duration.ofSeconds(15);

    private final List<String> destinations;
    private final PeriodicSnapshot<Map<String, Stats>> snapshot;
    private final Clock clock;

    OutboxMetrics(JdbcClient jdbcClient, List<String> destinations, Clock clock) {
        this.destinations = List.copyOf(destinations);
        this.snapshot = new PeriodicSnapshot<>(() -> read(jdbcClient), Map.of(), MAX_AGE, clock);
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String destination : destinations) {
            Gauge.builder(FAILED, this, metrics -> metrics.stats(destination).failed())
                    .tag("destination", destination)
                    .description("Mensagens do outbox que desistiram depois de todas as tentativas")
                    .register(registry);
            TimeGauge.builder(OLDEST_AGE, this, TimeUnit.SECONDS, metrics -> metrics.oldestAgeSeconds(destination))
                    .tag("destination", destination)
                    .description("Idade da mensagem mais antiga do outbox ainda não enviada")
                    .register(registry);
        }
    }

    private Stats stats(String destination) {
        return snapshot.get().getOrDefault(destination, Stats.EMPTY);
    }

    private double oldestAgeSeconds(String destination) {
        Instant oldest = stats(destination).oldestWaiting();
        return oldest == null ? 0 : Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
    }

    private static Map<String, Stats> read(JdbcClient jdbcClient) {
        return jdbcClient.sql("""
                        SELECT destination,
                               count(*) FILTER (WHERE status = 'FAILED') AS failed,
                               min(created_at) FILTER (WHERE status IN ('PENDING', 'PROCESSING')) AS oldest
                          FROM outbox_message
                         WHERE status <> 'DONE'
                         GROUP BY destination""")
                .query((row, index) -> Map.entry(row.getString("destination"),
                        new Stats(row.getLong("failed"), SqlTime.instantOrNull(row, "oldest"))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private record Stats(long failed, Instant oldestWaiting) {
        static final Stats EMPTY = new Stats(0, null);
    }
}
