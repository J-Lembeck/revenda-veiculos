package com.revenda.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Conta as chamadas em memória, por instância. Os baldes parados saem sozinhos e o total tem teto, para a
 * própria contagem não virar um jeito de esgotar a memória.
 */
public class RateLimiter {

    private static final long MAX_BUCKETS = 200_000;
    private static final Duration IDLE_BUCKET_TTL = Duration.ofMinutes(15);

    private final boolean enabled;
    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> buckets;

    public RateLimiter(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS)
                .expireAfterAccess(IDLE_BUCKET_TTL)
                .ticker(timeMeter::currentTimeNanos)
                .build();
    }

    /** Vazio quando a chamada cabe no limite; senão, quanto falta para caber de novo. */
    public Optional<Duration> tryConsume(RateLimitRule rule, String origin) {
        if (!enabled) {
            return Optional.empty();
        }
        Bucket bucket = buckets.get(rule.key() + '|' + origin, ignored -> newBucket(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed()
                ? Optional.empty()
                : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket(RateLimitRule rule) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(rule.capacity()).refillIntervally(rule.capacity(), rule.window()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return TimeUnit.MILLISECONDS.toNanos(clock.millis());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
