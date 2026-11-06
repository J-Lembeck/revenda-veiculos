package com.revenda.sales.infrastructure.adapters.out.messaging;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** A fila FIFO para onde vão os comandos do estoque (liberar, vender, entregar). */
@Validated
@ConfigurationProperties("revenda.vehicle-commands")
public record VehicleCommandsProperties(@NotBlank String queueUrl) {
}
