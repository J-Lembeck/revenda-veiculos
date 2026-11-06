package com.revenda.common.messaging;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Liga o registro de mensagens tratadas no serviço que tem banco. */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBean(JdbcClient.class)
public class ProcessedMessagesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProcessedMessages processedMessages(DataSource dataSource, JdbcClient jdbcClient) {
        return new JdbcProcessedMessages(dataSource, jdbcClient);
    }
}
