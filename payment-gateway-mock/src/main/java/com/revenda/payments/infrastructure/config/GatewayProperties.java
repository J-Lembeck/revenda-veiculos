package com.revenda.payments.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code apiKey}: a chave aceita no {@code X-Api-Key}. {@code webhookUrl} e {@code webhookSecret}: para onde vai o
 * webhook e o segredo da assinatura. {@code webhookRetryDelays}: a espera antes de cada reentrega, quando o destino não
 * responde {@code 2xx}. {@code webhookTimeout}: o limite de cada envio. Chave e segredo não têm padrão.
 */
@Validated
@ConfigurationProperties("revenda.gateway")
public record GatewayProperties(
        @NotBlank String apiKey,
        @NotNull URI webhookUrl,
        @NotBlank String webhookSecret,
        @NotEmpty @DefaultValue({"1s", "5s", "25s"}) List<Duration> webhookRetryDelays,
        @NotNull @DefaultValue("2s") Duration webhookTimeout) {

    @Override
    public String toString() {
        return "GatewayProperties[webhookUrl=" + webhookUrl + ", webhookRetryDelays=" + webhookRetryDelays
                + ", segredos omitidos]";
    }
}
