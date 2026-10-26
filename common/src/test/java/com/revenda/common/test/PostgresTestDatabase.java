package com.revenda.common.test;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Um Postgres só para a suíte inteira, na mesma imagem do ambiente local. Sobe no bloco estático e nunca é
 * parado: {@code @Testcontainers}/{@code @Container} derrubariam o contêiner ao fim da primeira classe.
 */
public final class PostgresTestDatabase {

    private static final PostgreSQLContainer POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"))
                .withDatabaseName("revenda_test")
                .withUsername("revenda_test")
                .withPassword(UUID.randomUUID().toString().replace("-", ""));
        POSTGRES.start();
    }

    private PostgresTestDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
