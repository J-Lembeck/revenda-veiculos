package com.revenda.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OutboxAutoConfigurationTests {

    private enum Destination implements OutboxDestination {
        VEHICLE_COMMANDS
    }

    private final DataSource dataSource = mock(DataSource.class);

    private final ApplicationContextRunner withDatabase = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ObjectMapper.class, JsonMapper::new)
            .withBean(DataSource.class, () -> dataSource)
            .withBean(JdbcClient.class, () -> JdbcClient.create(dataSource))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

    @Test
    void serviceThatSendsNothingGetsNoOutbox() {
        withDatabase.run(context -> assertThat(context)
                .doesNotHaveBean(OutboxPublisher.class)
                .doesNotHaveBean(OutboxDispatcher.class));
    }

    @Test
    void serviceWithASenderGetsThePublisherAndTheDispatcher() {
        withDatabase.withBean("vehicleCommands", OutboxSender.class, () -> sender())
                .run(context -> assertThat(context)
                        .hasSingleBean(OutboxDispatcher.class)
                        .getBean(OutboxPublisher.class).isInstanceOf(JdbcOutboxPublisher.class));
    }

    @Test
    void twoSendersForTheSameDestinationStopTheStartup() {
        withDatabase.withBean("first", OutboxSender.class, () -> sender())
                .withBean("second", OutboxSender.class, () -> sender())
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("Dois envios para o destino VEHICLE_COMMANDS"));
    }

    @Test
    void maxAttemptsBelowOneStopsTheStartup() {
        withDatabase.withBean("vehicleCommands", OutboxSender.class, () -> sender())
                .withPropertyValues("revenda.outbox.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    private static OutboxSender sender() {
        return new OutboxSender() {
            @Override
            public OutboxDestination destination() {
                return Destination.VEHICLE_COMMANDS;
            }

            @Override
            public void send(OutboxMessage message) {
            }
        };
    }
}
