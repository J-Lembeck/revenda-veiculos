package com.revenda.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AuditAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ObjectMapper.class, JsonMapper::new);

    @Test
    void serviceWithoutDatabaseGetsNoRecorder() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AuditRecorder.class));
    }

    @Test
    void serviceWithJdbcClientGetsTheJdbcRecorder() {
        DataSource dataSource = mock(DataSource.class);

        runner.withBean(DataSource.class, () -> dataSource)
                .withBean(JdbcClient.class, () -> JdbcClient.create(dataSource))
                .run(context -> assertThat(context).hasSingleBean(AuditRecorder.class)
                        .getBean(AuditRecorder.class).isInstanceOf(JdbcAuditRecorder.class));
    }
}
