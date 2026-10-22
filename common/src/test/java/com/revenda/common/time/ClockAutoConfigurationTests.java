package com.revenda.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockAutoConfigurationTests {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClockAutoConfiguration.class));

    @Test
    void providesTheSystemClockInUtc() {
        context.run(app -> assertThat(app.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC));
    }

    @Test
    void stepsAsideWhenTheApplicationDefinesItsOwnClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-22T14:32:00Z"), ZoneOffset.UTC);

        context.withBean(Clock.class, () -> fixed)
                .run(app -> assertThat(app.getBean(Clock.class)).isSameAs(fixed));
    }
}
