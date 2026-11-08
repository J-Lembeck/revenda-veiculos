package com.revenda.sales.infrastructure.config;

import com.revenda.sales.application.service.SalesProperties;
import com.revenda.sales.infrastructure.adapters.out.document.DocumentsProperties;
import com.revenda.sales.infrastructure.adapters.out.messaging.VehicleCommandsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Os prazos da saga, o cliente da fila de comandos do estoque e o do bucket dos documentos. Região e credenciais vêm da
 * cadeia padrão do SDK ({@code AWS_REGION}); o endereço do LocalStack, do {@code AWS_ENDPOINT_URL} — o cliente do S3 e
 * o assinador de links o recebem explícito, com o bucket no caminho.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SalesProperties.class, VehicleCommandsProperties.class, DocumentsProperties.class})
class SalesConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    SqsClient sqsClient() {
        return SqsClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    S3Client s3Client(DocumentsProperties documents) {
        var builder = S3Client.builder().httpClient(UrlConnectionHttpClient.create());
        if (documents.endpoint() != null) {
            builder.endpointOverride(documents.endpoint()).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    S3Presigner s3Presigner(DocumentsProperties documents) {
        var builder = S3Presigner.builder();
        if (documents.linkEndpoint() != null) {
            builder.endpointOverride(documents.linkEndpoint())
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
