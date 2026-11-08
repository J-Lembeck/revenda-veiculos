package com.revenda.sales.infrastructure.config;

import com.revenda.common.http.ServiceClients;
import com.revenda.sales.infrastructure.adapters.out.http.NeighborsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Os clientes HTTP dos vizinhos, todos com os prazos curtos do caminho síncrono. O estoque e o cadastro recebem o
 * token do próprio {@code sales} (client credentials, nunca o token do cliente); o gateway recebe a chave de API.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NeighborsProperties.class)
class NeighborClientsConfiguration {

    private static final String API_KEY = "X-Api-Key";

    @Bean
    OAuth2AuthorizedClientManager serviceTokens(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients, NeighborsProperties properties) {
        return ServiceClients.serviceTokens(registrations, authorizedClients, properties.connectTimeout(),
                properties.readTimeout());
    }

    @Bean
    RestClient vehiclesRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager serviceTokens,
            NeighborsProperties properties) {
        return builder.clone()
                .baseUrl(properties.vehiclesUrl())
                .requestFactory(requestFactory(properties))
                .requestInterceptor(ServiceClients.serviceToken(serviceTokens, properties.clientRegistration()))
                .build();
    }

    @Bean
    RestClient customersRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager serviceTokens,
            NeighborsProperties properties) {
        return builder.clone()
                .baseUrl(properties.customersUrl())
                .requestFactory(requestFactory(properties))
                .requestInterceptor(ServiceClients.serviceToken(serviceTokens, properties.clientRegistration()))
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

    private static ClientHttpRequestFactory requestFactory(NeighborsProperties properties) {
        return ServiceClients.requestFactory(properties.connectTimeout(), properties.readTimeout());
    }
}
