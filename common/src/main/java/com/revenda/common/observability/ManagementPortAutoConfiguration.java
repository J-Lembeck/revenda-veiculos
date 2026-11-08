package com.revenda.common.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;

/**
 * O Actuator só existe na porta de gestão: na porta do serviço, o {@code health} e o {@code prometheus}, que abrem
 * sem token, ficariam expostos junto com a API. Por isso a subida falha se {@code management.server.port} faltar ou
 * for a mesma de {@code server.port}; negativa desliga o Actuator pela web e é aceita.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType")
public class ManagementPortAutoConfiguration {

    static final String MANAGEMENT_PORT = "management.server.port";
    static final String SERVER_PORT = "server.port";
    private static final int DEFAULT_SERVER_PORT = 8080;

    ManagementPortAutoConfiguration(Environment environment) {
        requireOwnPort(environment);
    }

    static void requireOwnPort(Environment environment) {
        Integer management = environment.getProperty(MANAGEMENT_PORT, Integer.class);
        if (management != null && management < 0) {
            return;
        }
        int server = environment.getProperty(SERVER_PORT, Integer.class, DEFAULT_SERVER_PORT);
        if (management == null || (management != 0 && management == server)) {
            throw new IllegalStateException("O Actuator precisa de uma porta de gestão própria: " + MANAGEMENT_PORT
                    + " (" + management + ") tem de ser diferente de " + SERVER_PORT + " (" + server + ")");
        }
    }
}
