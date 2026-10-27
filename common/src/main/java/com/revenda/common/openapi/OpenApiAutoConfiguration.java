package com.revenda.common.openapi;

import com.revenda.common.security.SecurityRoutes;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Liga as convenções do contrato no serviço que tem o springdoc. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenApiCustomizer.class)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    @Bean
    OpenApiCustomizer openApiConventions(ObjectProvider<SecurityRoutes> routes, OpenApiProperties properties) {
        return new OpenApiConventions(routes.orderedStream().toList(), properties);
    }
}
