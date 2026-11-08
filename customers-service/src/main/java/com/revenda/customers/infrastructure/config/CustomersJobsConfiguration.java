package com.revenda.customers.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga o job de retenção. {@code CUSTOMERS_JOBS_ENABLED=false} desliga: os testes chamam a passada quando querem. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "revenda.customers.jobs-enabled", havingValue = "true")
class CustomersJobsConfiguration {
}
