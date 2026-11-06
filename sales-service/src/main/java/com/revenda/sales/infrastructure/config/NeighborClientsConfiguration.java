package com.revenda.sales.infrastructure.config;

import com.revenda.sales.infrastructure.adapters.out.http.NeighborsProperties;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Os clientes HTTP dos vizinhos, todos com os prazos curtos do caminho síncrono. O estoque e o cadastro recebem o
 * token do próprio {@code sales} (client credentials, nunca o token do cliente), pedido ao provedor de identidade com
 * os mesmos prazos e guardado para o serviço inteiro; o gateway recebe a chave de API.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NeighborsProperties.class)
class NeighborClientsConfiguration {

    private static final String API_KEY = "X-Api-Key";

    @Bean
    OAuth2AuthorizedClientManager serviceTokens(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients, NeighborsProperties properties) {
        RestClientClientCredentialsTokenResponseClient tokenClient =
                new RestClientClientCredentialsTokenResponseClient();
        tokenClient.setRestClient(RestClient.builder()
                .requestFactory(requestFactory(properties))
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

    @Bean
    RestClient vehiclesRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager serviceTokens,
            NeighborsProperties properties) {
        return builder.clone()
                .baseUrl(properties.vehiclesUrl())
                .requestFactory(requestFactory(properties))
                .requestInterceptor(serviceToken(serviceTokens, properties))
                .build();
    }

    @Bean
    RestClient customersRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager serviceTokens,
            NeighborsProperties properties) {
        return builder.clone()
                .baseUrl(properties.customersUrl())
                .requestFactory(requestFactory(properties))
                .requestInterceptor(serviceToken(serviceTokens, properties))
                .build();
    }

    @Bean
    RestClient gatewayRestClient(RestClient.Builder builder, NeighborsProperties properties) {
        return builder.clone()
                .baseUrl(properties.gatewayUrl())
                .requestFactory(requestFactory(properties))
                .defaultHeader(API_KEY, properties.gatewayApiKey())
                .build();
    }

    private static OAuth2ClientHttpRequestInterceptor serviceToken(OAuth2AuthorizedClientManager serviceTokens,
            NeighborsProperties properties) {
        Authentication service = UsernamePasswordAuthenticationToken.authenticated(properties.clientRegistration(),
                null, List.of());
        OAuth2ClientHttpRequestInterceptor interceptor = new OAuth2ClientHttpRequestInterceptor(serviceTokens);
        interceptor.setClientRegistrationIdResolver(request -> properties.clientRegistration());
        interceptor.setPrincipalResolver(request -> service);
        return interceptor;
    }

    private static ClientHttpRequestFactory requestFactory(NeighborsProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
