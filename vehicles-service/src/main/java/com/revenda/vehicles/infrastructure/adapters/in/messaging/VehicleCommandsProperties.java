package com.revenda.vehicles.infrastructure.adapters.in.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A fila FIFO dos comandos que a compra manda ao estoque. {@code waitTime} é a espera do <i>long polling</i> (até
 * 20 s); {@code retryDelay}, a pausa quando a fila não responde.
 */
@ConfigurationProperties("revenda.vehicle-commands")
public record VehicleCommandsProperties(
        boolean enabled,
        String queueUrl,
        @DefaultValue("20s") Duration waitTime,
        @DefaultValue("5s") Duration retryDelay) {

    public VehicleCommandsProperties {
        if (enabled && (queueUrl == null || queueUrl.isBlank())) {
            throw new IllegalArgumentException("revenda.vehicle-commands.queue-url é obrigatória com o consumidor ligado");
        }
        if (waitTime.isNegative() || waitTime.toSeconds() > 20) {
            throw new IllegalArgumentException("revenda.vehicle-commands.wait-time vai de 0 a 20 s");
        }
    }
}
