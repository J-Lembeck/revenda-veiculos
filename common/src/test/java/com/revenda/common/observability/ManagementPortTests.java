package com.revenda.common.observability;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ManagementPortTests {

    @Test
    void anotherManagementPortIsAccepted() {
        assertThatCode(() -> check("9083", "8083")).doesNotThrowAnyException();
    }

    @Test
    void randomPortsInTestsAreAccepted() {
        assertThatCode(() -> check("0", "0")).doesNotThrowAnyException();
    }

    @Test
    void aDisabledManagementPortIsAccepted() {
        assertThatCode(() -> check("-1", "8083")).doesNotThrowAnyException();
    }

    @Test
    void theServicePortIsRefused() {
        assertThatThrownBy(() -> check("8083", "8083"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("management.server.port (8083)")
                .hasMessageContaining("server.port (8083)");
    }

    @Test
    void theDefaultServicePortIsRefused() {
        assertThatThrownBy(() -> check("8080", null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutAManagementPortTheActuatorWouldBeOnTheServicePort() {
        assertThatThrownBy(() -> check(null, "8083")).isInstanceOf(IllegalStateException.class);
    }

    private static void check(String management, String server) {
        MockEnvironment environment = new MockEnvironment();
        if (management != null) {
            environment.setProperty(ManagementPortAutoConfiguration.MANAGEMENT_PORT, management);
        }
        if (server != null) {
            environment.setProperty(ManagementPortAutoConfiguration.SERVER_PORT, server);
        }
        ManagementPortAutoConfiguration.requireOwnPort(environment);
    }
}
