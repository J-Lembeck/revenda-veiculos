package com.revenda.common.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Cifra de campo e índice cego no serviço que tem banco e chave-mestra. */
@AutoConfiguration(after = KmsAutoConfiguration.class,
        afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBean({JdbcClient.class, KeyManagementPort.class})
public class CryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DataKeys dataKeys(JdbcClient jdbcClient, KeyManagementPort keyManagement) {
        return new JdbcDataKeys(jdbcClient, keyManagement);
    }

    @Bean
    @ConditionalOnMissingBean
    FieldCipher fieldCipher(DataKeys dataKeys) {
        return new FieldCipher(dataKeys);
    }

    @Bean
    @ConditionalOnMissingBean
    BlindIndexer blindIndexer(DataKeys dataKeys) {
        return new BlindIndexer(dataKeys);
    }
}
