package com.revenda.vehicles.infrastructure.config;

import com.revenda.vehicles.infrastructure.adapters.in.messaging.VehicleCommandsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * O cliente da fila de comandos, só com o consumidor ligado. Região, credenciais e o endereço do LocalStack vêm da
 * cadeia padrão do SDK ({@code AWS_REGION}, {@code AWS_ENDPOINT_URL}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "revenda.vehicle-commands.enabled", havingValue = "true")
@EnableConfigurationProperties(VehicleCommandsProperties.class)
class VehicleCommandsConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    SqsClient sqsClient() {
        return SqsClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
    }
}
