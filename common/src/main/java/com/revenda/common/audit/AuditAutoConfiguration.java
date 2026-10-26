package com.revenda.common.audit;

import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBean(JdbcClient.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditRecorder auditRecorder(DataSource dataSource, JdbcClient jdbcClient, ObjectMapper objectMapper, Clock clock) {
        return new JdbcAuditRecorder(dataSource, jdbcClient, objectMapper, clock);
    }
}
