package com.revenda.customers.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code requireVerifiedEmail}: o cadastro só aceita conta com o e-mail verificado pelo provedor de identidade
 * ({@code email_verified} no token). Desligado no ambiente local, ligado na nuvem.
 */
@ConfigurationProperties("revenda.customers")
public record CustomersProperties(boolean requireVerifiedEmail) {
}
