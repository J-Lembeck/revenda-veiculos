package com.revenda.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.test.TestTokens;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * As chaves públicas por um endereço que não é o do emissor, como o contêiner do compose lê do Keycloak: a
 * assinatura confere pelas chaves desse endereço, e o {@code iss} continua o do emissor configurado.
 */
class JwkSetUriTests {

    private HttpServer keys;

    @BeforeEach
    void publishTheKeys() throws IOException {
        keys = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keys.createContext("/certs", exchange -> {
            byte[] body = TestTokens.publicJwkSet().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        keys.start();
    }

    @AfterEach
    void stop() {
        keys.stop(0);
    }

    @Test
    void theTokenOfTheIssuerIsAcceptedWithTheKeysFromTheOtherAddress() {
        JwtDecoder decoder = decoder(TestTokens.ISSUER);

        assertThat(decoder.decode(TestTokens.customer("ana")).getSubject()).isEqualTo("ana");
    }

    @Test
    void aTokenOfAnotherIssuerIsRefusedEvenSignedByTheSameKey() {
        JwtDecoder decoder = decoder(TestTokens.ISSUER);
        String other = TestTokens.token().subject("ana").client(TestTokens.WEB_CLIENT)
                .issuer("http://keycloak:8180/realms/revenda").roles("CUSTOMER").encode();

        assertThatThrownBy(() -> decoder.decode(other)).isInstanceOf(JwtException.class);
    }

    @Test
    void aBlankAddressMeansTheIssuersOwn() {
        ResourceServerProperties.Oidc oidc = new ResourceServerProperties.Oidc(TestTokens.ISSUER, "realm_access.roles",
                "", List.of(TestTokens.WEB_CLIENT), List.of(), " ");

        assertThat(oidc.jwkSetUri()).isNull();
    }

    private JwtDecoder decoder(String issuer) {
        ResourceServerProperties.Oidc oidc = new ResourceServerProperties.Oidc(issuer, "realm_access.roles", "",
                List.of(TestTokens.WEB_CLIENT), List.of(TestTokens.SALES_CLIENT),
                "http://127.0.0.1:" + keys.getAddress().getPort() + "/certs");
        return new ResourceServerAutoConfiguration().jwtDecoder(
                new ResourceServerProperties(oidc, new ResourceServerProperties.RateLimit(true)));
    }
}
