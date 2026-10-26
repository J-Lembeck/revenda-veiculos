package com.revenda.common.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * Liga o KMS só no serviço que declara {@code revenda.crypto.kms-key-id}. Região, credenciais e o
 * endereço do LocalStack vêm da cadeia padrão do SDK ({@code AWS_REGION}, {@code AWS_ENDPOINT_URL}).
 */
@AutoConfiguration
@ConditionalOnClass(KmsClient.class)
@ConditionalOnProperty("revenda.crypto.kms-key-id")
@ConditionalOnMissingBean(KeyManagementPort.class)
@EnableConfigurationProperties(CryptoProperties.class)
public class KmsAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    KmsClient kmsClient() {
        return KmsClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
    }

    @Bean
    KeyManagementPort keyManagement(KmsClient kmsClient, CryptoProperties properties) {
        return new KmsKeyManagement(kmsClient, properties.kmsKeyId());
    }
}
