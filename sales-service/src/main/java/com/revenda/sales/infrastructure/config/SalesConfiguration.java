package com.revenda.sales.infrastructure.config;

import com.revenda.sales.application.service.SalesProperties;
import com.revenda.sales.infrastructure.adapters.out.messaging.VehicleCommandsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Os prazos da saga e o cliente da fila de comandos do estoque. Região, credenciais e o endereço do LocalStack vêm da
 * cadeia padrão do SDK ({@code AWS_REGION}, {@code AWS_ENDPOINT_URL}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SalesProperties.class, VehicleCommandsProperties.class})
class SalesConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    SqsClient sqsClient() {
        return SqsClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
    }
}
