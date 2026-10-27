package com.revenda.customers.infrastructure.config;

import com.revenda.customers.application.service.CustomersProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomersProperties.class)
class CustomersConfiguration {
}
