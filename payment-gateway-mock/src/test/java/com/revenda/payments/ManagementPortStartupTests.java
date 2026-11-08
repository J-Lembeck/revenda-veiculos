package com.revenda.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.observability.ManagementPortAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/** Com o Actuator no classpath, a subida falha se a porta de gestão for a do serviço. */
class ManagementPortStartupTests {

    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ManagementPortAutoConfiguration.class));

    @Test
    void theServiceDoesNotStartWithTheActuatorOnItsOwnPort() {
        context.withPropertyValues("server.port=8084", "management.server.port=8084")
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("porta de gestão própria"));
    }

    @Test
    void theServiceStartsWithAManagementPortOfItsOwn() {
        context.withPropertyValues("server.port=8084", "management.server.port=9084")
                .run(started -> assertThat(started).hasNotFailed()
                        .hasSingleBean(ManagementPortAutoConfiguration.class));
    }
}
