package com.revenda.customers.application.service;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code requireVerifiedEmail}: o cadastro só aceita conta com o e-mail verificado pelo provedor de identidade
 * ({@code email_verified} no token). Desligado no ambiente local, ligado na nuvem. {@code zone}: o fuso da data civil
 * da compra, a que conta a maioridade (RN-05).
 */
@ConfigurationProperties("revenda.customers")
public record CustomersProperties(boolean requireVerifiedEmail, @DefaultValue("America/Sao_Paulo") ZoneId zone) {
}
