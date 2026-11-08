package com.revenda.customers.infrastructure.config;

import com.revenda.common.http.ServiceClients;
import com.revenda.customers.infrastructure.adapters.out.http.SalesNeighborProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/** O cliente HTTP do serviço de vendas, com o token do próprio {@code customers} e prazos curtos. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SalesNeighborProperties.class)
class SalesClientConfiguration {

    @Bean
    OAuth2AuthorizedClientManager serviceTokens(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients, SalesNeighborProperties properties) {
        return ServiceClients.serviceTokens(registrations, authorizedClients, properties.connectTimeout(),
                properties.readTimeout());
    }

    @Bean
    RestClient salesRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager serviceTokens,
            SalesNeighborProperties properties) {
        return builder.clone()
                .baseUrl(properties.salesUrl())
                .requestFactory(ServiceClients.requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .requestInterceptor(ServiceClients.serviceToken(serviceTokens, properties.clientRegistration()))
                .build();
    }
}
