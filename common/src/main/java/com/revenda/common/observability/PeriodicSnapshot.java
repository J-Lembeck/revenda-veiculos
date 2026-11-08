package com.revenda.common.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * O último valor lido de uma fonte cara, como uma consulta ao banco, relido no máximo uma vez por intervalo. Serve
 * às métricas lidas a cada coleta: a coleta não vira uma consulta. Se a leitura falha, fica o valor anterior.
 */
@Slf4j
public final class PeriodicSnapshot<T> {

    private final Supplier<T> source;
    private final Duration maxAge;
    private final Clock clock;
    private T value;
    private Instant readAt;

    public PeriodicSnapshot(Supplier<T> source, T initial, Duration maxAge, Clock clock) {
        this.source = source;
        this.value = initial;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    public synchronized T get() {
        Instant now = clock.instant();
        if (readAt == null || now.isBefore(readAt) || !now.isBefore(readAt.plus(maxAge))) {
            readAt = now;
            try {
                value = source.get();
            } catch (RuntimeException e) {
                log.warn("Leitura de métrica falhou; segue o valor anterior", e);
            }
        }
        return value;
    }
}
