package com.revenda.common.outbox;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("revenda.outbox")
public record OutboxProperties(@DefaultValue("10") @Min(1) int maxAttempts) {
}
