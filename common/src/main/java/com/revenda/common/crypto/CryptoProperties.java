package com.revenda.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("revenda.crypto")
public record CryptoProperties(String kmsKeyId) {
}
