package com.revenda.common.security;

import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Cada serviço que tem o starter de resource server é protegido por esta cadeia: token do provedor de
 * identidade, papéis e scopes do token, regras declaradas pelo serviço em {@link SecurityRoutes} e limite de
 * taxa. Sem {@code UserDetailsService}, o Boot não gera senha.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(BearerTokenAuthenticationFilter.class)
@EnableConfigurationProperties(ResourceServerProperties.class)
public class ResourceServerAutoConfiguration {

    private static final String[] HEALTH = {"/actuator/health", "/actuator/health/**"};
    private static final String[] API_DOCS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};
    private static final String INTERNAL = "/internal/**";

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(ResourceServerProperties properties) {
        ResourceServerProperties.Oidc oidc = properties.oidc();
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(oidc.issuerUri()).build();
            decoder.setJwtValidator(AccessTokenValidator.forIssuer(oidc));
            return decoder;
        });
    }

    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Role.ADMIN.name()).implies(Role.SELLER.name())
                .build();
    }

    @Bean
    RateLimiter rateLimiter(ResourceServerProperties properties, Clock clock) {
        return new RateLimiter(properties.rateLimit().enabled(), clock);
    }

    @Bean
    SecurityExceptionPassthrough securityExceptionPassthrough() {
        return new SecurityExceptionPassthrough();
    }

    @Bean
    SecurityFilterChain resourceServerFilterChain(HttpSecurity http, ResourceServerProperties properties,
            ObjectProvider<SecurityRoutes> serviceRoutes, RateLimiter rateLimiter, ApiErrorWriter errorWriter,
            ObjectProvider<PathPatternRequestMatcher.Builder> matchers) {
        List<SecurityRoutes> declared = serviceRoutes.orderedStream().toList();
        Map<Boolean, List<Route>> byInternal = declared.stream()
                .flatMap(routes -> routes.routes().stream())
                .collect(Collectors.partitioningBy(route -> route.scope() != null));
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(errorWriter);
        RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(errorWriter);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiter,
                declared.stream().flatMap(routes -> routes.rateLimits().stream()).toList(),
                matchers.getIfAvailable(PathPatternRequestMatcher::withDefaults), errorWriter);

        http.csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(HttpMethod.GET, HEALTH).permitAll();
                    auth.requestMatchers(HttpMethod.GET, API_DOCS).permitAll();
                    byInternal.get(true).forEach(route -> authorize(auth, route));
                    auth.requestMatchers(INTERNAL).denyAll();
                    byInternal.get(false).forEach(route -> authorize(auth, route));
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new AccessTokenConverter(properties.oidc())))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new ActorContextFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private static void authorize(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth, Route route) {
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl url = route.method() == null
                ? auth.requestMatchers(route.pattern())
                : auth.requestMatchers(route.method(), route.pattern());
        if (route.scope() != null) {
            url.hasAuthority(AccessTokenConverter.scopeAuthority(route.scope()));
        } else if (route.roles().isEmpty()) {
            url.permitAll();
        } else {
            url.hasAnyRole(route.roles().stream().map(Role::name).toArray(String[]::new));
        }
    }
}
