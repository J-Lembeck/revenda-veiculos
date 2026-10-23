package com.revenda.common.test;

import com.revenda.common.security.ResourceServerProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration(proxyBeanMethods = false)
public class TestTokenConfiguration {

    @Bean
    JwtDecoder testJwtDecoder(ResourceServerProperties properties) {
        return TestTokens.decoder(properties.oidc());
    }
}
