package com.revenda.common.idempotency;

import com.revenda.common.error.web.ApiErrorWriter;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Liga a idempotência no serviço que tem banco. O filtro tem a ordem padrão, depois da cadeia de
 * segurança: pedido sem token é recusado antes de o corpo ser lido.
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBean(JdbcClient.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IdempotencyStore idempotencyStore(DataSource dataSource, JdbcClient jdbcClient, ObjectMapper objectMapper,
            Clock clock) {
        return new JdbcIdempotencyStore(dataSource, jdbcClient, objectMapper, clock);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(ApiErrorWriter errorWriter) {
        return new FilterRegistrationBean<>(new IdempotencyFilter(errorWriter));
    }
}
