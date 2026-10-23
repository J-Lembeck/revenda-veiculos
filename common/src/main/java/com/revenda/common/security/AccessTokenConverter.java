package com.revenda.common.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Token de cliente de serviço vira {@code SCOPE_*} e nenhum papel; token de pessoa vira {@code ROLE_*} e nenhum
 * scope. Assim token de pessoa nunca passa numa rota interna e token de serviço nunca passa numa rota de papel.
 */
@RequiredArgsConstructor
public class AccessTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String SCOPE_CLAIM = "scope";
    private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

    private final ResourceServerProperties.Oidc oidc;

    public static String scopeAuthority(String scope) {
        return SCOPE_AUTHORITY_PREFIX + scope;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String client = AccessTokenValidator.clientOf(jwt);
        if (client != null && oidc.serviceClients().contains(client)) {
            return new ActorAuthentication(jwt, scopeAuthorities(jwt), client, ActorType.SERVICE, null);
        }
        Set<Role> roles = roles(jwt);
        List<GrantedAuthority> authorities = roles.stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
        Role highest = roles.stream().max(Comparator.naturalOrder()).orElse(null);
        return new ActorAuthentication(jwt, authorities, jwt.getSubject(), ActorType.USER, highest);
    }

    private Set<Role> roles(Jwt jwt) {
        return values(claimAt(jwt.getClaims(), oidc.rolesClaim())).stream()
                .map(Role::of)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }

    private List<GrantedAuthority> scopeAuthorities(Jwt jwt) {
        String prefix = oidc.scopePrefix();
        return values(jwt.getClaims().get(SCOPE_CLAIM)).stream()
                .filter(scope -> scope.startsWith(prefix) && scope.length() > prefix.length())
                .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority(scopeAuthority(scope.substring(prefix.length()))))
                .toList();
    }

    private static Object claimAt(Map<String, Object> claims, String path) {
        Object current = claims;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static List<String> values(Object claim) {
        if (claim instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (claim instanceof String text) {
            return Arrays.stream(text.split(" ")).filter(value -> !value.isEmpty()).toList();
        }
        return List.of();
    }
}
