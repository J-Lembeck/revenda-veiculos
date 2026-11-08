package com.revenda.common.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * As peças de um cliente HTTP entre serviços: prazos curtos de conexão e leitura e o token do próprio serviço
 * (client credentials, nunca o token de quem chamou), pedido ao provedor de identidade com os mesmos prazos e guardado
 * para o serviço inteiro.
 */
public final class ServiceClients {

    private ServiceClients() {
    }

    /** Quem pede e guarda o token de serviço das registrações do {@code spring.security.oauth2.client}. */
    public static OAuth2AuthorizedClientManager serviceTokens(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients, Duration connectTimeout, Duration readTimeout) {
        RestClientClientCredentialsTokenResponseClient tokenClient =
                new RestClientClientCredentialsTokenResponseClient();
        tokenClient.setRestClient(RestClient.builder()
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .messageConverters(List.of(new FormHttpMessageConverter(),
                        new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(clientCredentials -> clientCredentials.accessTokenResponseClient(tokenClient))
                .build());
        return manager;
    }

    /** Põe o token de serviço da {@code registration} em cada chamada. */
    public static OAuth2ClientHttpRequestInterceptor serviceToken(OAuth2AuthorizedClientManager serviceTokens,
            String registration) {
        Authentication service = UsernamePasswordAuthenticationToken.authenticated(registration, null, List.of());
        OAuth2ClientHttpRequestInterceptor interceptor = new OAuth2ClientHttpRequestInterceptor(serviceTokens);
        interceptor.setClientRegistrationIdResolver(request -> registration);
        interceptor.setPrincipalResolver(request -> service);
        return interceptor;
    }

    public static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build());
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
