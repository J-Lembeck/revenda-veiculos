package com.revenda.payments.infrastructure.config;

import com.revenda.common.error.web.ApiErrorWriter;
import com.revenda.common.webhook.WebhookSignature;
import com.revenda.payments.infrastructure.adapters.in.web.ApiKeyFilter;
import jakarta.servlet.DispatcherType;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class GatewayConfiguration {

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(GatewayProperties properties, ApiErrorWriter errors) {
        FilterRegistrationBean<ApiKeyFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyFilter(properties.apiKey(), errors));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    @Bean
    WebhookSignature webhookSignature(GatewayProperties properties) {
        return new WebhookSignature(properties.webhookSecret());
    }

    @Bean
    HttpClient webhookHttpClient(GatewayProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.webhookTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
