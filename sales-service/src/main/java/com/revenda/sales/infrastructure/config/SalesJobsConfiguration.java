package com.revenda.sales.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga os jobs da saga (despachante do outbox e varredura de prazos). {@code SALES_JOBS_ENABLED=false} desliga: os
 * testes chamam cada passada na hora que querem.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "revenda.sales.jobs-enabled", havingValue = "true")
class SalesJobsConfiguration {
}
