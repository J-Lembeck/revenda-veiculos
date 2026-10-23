package com.revenda.common.security;

import java.util.HashSet;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Aceita só token de acesso emitido para um cliente conhecido. O cliente vem do {@code azp} (Keycloak) ou do
 * {@code client_id} (Cognito, que não põe {@code aud} no token de acesso); o ID token é recusado pelo
 * {@code typ} (Keycloak) ou pelo {@code token_use} (Cognito).
 */
public class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final String AUTHORIZED_PARTY = "azp";
    private static final String CLIENT_ID = "client_id";
    private static final String TOKEN_TYPE = "typ";
    private static final String ACCESS_TOKEN_TYPE = "Bearer";
    private static final String TOKEN_USE = "token_use";
    private static final String ACCESS_TOKEN_USE = "access";

    private final Set<String> clients;

    AccessTokenValidator(ResourceServerProperties.Oidc oidc) {
        Set<String> accepted = new HashSet<>(oidc.webClients());
        accepted.addAll(oidc.serviceClients());
        this.clients = Set.copyOf(accepted);
    }

    public static OAuth2TokenValidator<Jwt> forIssuer(ResourceServerProperties.Oidc oidc) {
        return JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(oidc.issuerUri()), new AccessTokenValidator(oidc));
    }

    static String clientOf(Jwt jwt) {
        String authorizedParty = jwt.getClaimAsString(AUTHORIZED_PARTY);
        return authorizedParty != null ? authorizedParty : jwt.getClaimAsString(CLIENT_ID);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!isAccessToken(token)) {
            return failure("Only access tokens are accepted.");
        }
        String client = clientOf(token);
        if (client == null || !clients.contains(client)) {
            return failure("The token was issued to a client this service does not accept.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean isAccessToken(Jwt token) {
        String type = token.getClaimAsString(TOKEN_TYPE);
        String use = token.getClaimAsString(TOKEN_USE);
        return (type == null || ACCESS_TOKEN_TYPE.equalsIgnoreCase(type))
                && (use == null || ACCESS_TOKEN_USE.equals(use));
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
