package com.revenda.common.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.revenda.common.security.AccessTokenValidator;
import com.revenda.common.security.ResourceServerProperties;
import com.revenda.common.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Tokens no formato do Keycloak local, assinados por um par de chaves gerado na memória a cada execução — nenhuma
 * chave fica em arquivo.
 */
public final class TestTokens {

    public static final String ISSUER = "https://idp.example.test/realms/revenda";
    public static final String WEB_CLIENT = "revenda-web";
    public static final String SALES_CLIENT = "sales-service";
    public static final String CUSTOMERS_CLIENT = "customers-service";

    private static final Clock CLOCK = Clock.systemUTC();
    private static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final RSAKey KEY = newKey();
    private static final RSAKey UNKNOWN_KEY = newKey();

    private TestTokens() {
    }

    public static String customer(String subject) {
        return person(subject, Role.CUSTOMER);
    }

    public static String seller(String subject) {
        return person(subject, Role.SELLER);
    }

    public static String admin(String subject) {
        return person(subject, Role.ADMIN);
    }

    public static String person(String subject, Role... roles) {
        return token().subject(subject).roles(Arrays.stream(roles).map(Role::name).toArray(String[]::new)).encode();
    }

    public static String service(String clientId, String... scopes) {
        return token().client(clientId).claim("client_id", clientId).scopes(scopes).encode();
    }

    public static Builder token() {
        return new Builder();
    }

    public static JwtDecoder decoder(ResourceServerProperties.Oidc oidc) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(AccessTokenValidator.forIssuer(oidc));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Builder {

        private final Map<String, Object> claims = new LinkedHashMap<>();
        private String issuer = ISSUER;
        private String subject = UUID.randomUUID().toString();
        private Instant issuedAt = CLOCK.instant();
        private RSAKey key = KEY;

        private Builder() {
            claims.put("typ", "Bearer");
            claims.put("azp", WEB_CLIENT);
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder client(String clientId) {
            return claim("azp", clientId);
        }

        public Builder roles(String... roles) {
            return claim("realm_access", Map.of("roles", List.of(roles)));
        }

        public Builder scopes(String... scopes) {
            return claim("scope", String.join(" ", scopes));
        }

        public Builder claim(String name, Object value) {
            if (value == null) {
                claims.remove(name);
            } else {
                claims.put(name, value);
            }
            return this;
        }

        public Builder issuedAgo(Duration age) {
            this.issuedAt = CLOCK.instant().minus(age);
            return this;
        }

        public Builder signedByUnknownKey() {
            this.key = UNKNOWN_KEY;
            return this;
        }

        public String encode() {
            JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(subject)
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plus(LIFETIME))
                    .claims(all -> all.putAll(claims))
                    .build();
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).type("JWT").build();
            NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
            return encoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
        }
    }
}
