package com.revenda.common.outbox;

import java.time.Clock;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Liga o outbox só no serviço que tem banco e declara ao menos um {@link OutboxSender}. */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"})
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class, OutboxSender.class})
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxPublisher outboxPublisher(DataSource dataSource, JdbcClient jdbcClient, ObjectMapper objectMapper,
            Clock clock) {
        return new JdbcOutboxPublisher(dataSource, jdbcClient, objectMapper, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxDispatcher outboxDispatcher(JdbcClient jdbcClient, List<OutboxSender> senders,
            PlatformTransactionManager transactionManager, OutboxProperties properties, Clock clock) {
        return new OutboxDispatcher(new OutboxQueue(jdbcClient), senders, new TransactionTemplate(transactionManager),
                properties.maxAttempts(), clock);
    }
}
