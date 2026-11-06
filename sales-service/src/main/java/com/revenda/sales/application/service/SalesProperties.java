package com.revenda.sales.application.service;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Os prazos da saga e a conferência do webhook: {@code paymentWindow} é a validade do código de pagamento (RN-11);
 * {@code recoveryThreshold}, a idade em que a compra parada no meio do caminho síncrono é dada como morta;
 * {@code webhookSecret} e {@code webhookTolerance}, o segredo HMAC e a janela do carimbo de tempo do aviso do gateway.
 */
@Validated
@ConfigurationProperties("revenda.sales")
public record SalesProperties(
        @DefaultValue("PT30M") Duration paymentWindow,
        @DefaultValue("PT2M") Duration recoveryThreshold,
        @DefaultValue("PT5M") Duration webhookTolerance,
        @NotBlank String webhookSecret) {

    public SalesProperties {
        if (paymentWindow.isNegative() || paymentWindow.isZero() || recoveryThreshold.isNegative()
                || recoveryThreshold.isZero() || webhookTolerance.isNegative()) {
            throw new IllegalArgumentException("Os prazos da saga são positivos");
        }
    }

    @Override
    public String toString() {
        return "SalesProperties[paymentWindow=" + paymentWindow + ", recoveryThreshold=" + recoveryThreshold
                + ", webhookTolerance=" + webhookTolerance + ", segredo omitido]";
    }
}
