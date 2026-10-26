package com.revenda.common.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Um Postgres só para a suíte inteira, na mesma imagem e preparado pelo mesmo {@code infra/postgres/init.sh} do
 * ambiente local: um banco por serviço, com as roles {@code <serviço>_owner} e {@code <serviço>_app}, e senhas
 * sorteadas a cada execução. Os serviços são lidos das chamadas {@code create_service_database} do próprio script.
 * Sobe no bloco estático e nunca é parado: {@code @Testcontainers}/{@code @Container} derrubariam o contêiner ao fim
 * da primeira classe.
 */
public final class PostgresTestDatabase {

    private static final Pattern SERVICE_CALL = Pattern.compile("^create_service_database (\\w+) ", Pattern.MULTILINE);
    private static final Path INIT_SCRIPT_SOURCE = initScript();

    public static final Set<String> SERVICES = servicesOf(INIT_SCRIPT_SOURCE);

    private static final String SECRET = randomSecret();
    private static final String INIT_SCRIPT = "/docker-entrypoint-initdb.d/init.sh";

    private static final PostgreSQLContainer POSTGRES;

    static {
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"))
                .withDatabaseName("revenda_test")
                .withUsername("revenda_test")
                .withPassword(randomSecret())
                .withCopyFileToContainer(MountableFile.forHostPath(INIT_SCRIPT_SOURCE, 0755), INIT_SCRIPT);
        for (String service : SERVICES) {
            String prefix = service.toUpperCase(Locale.ROOT) + "_DB_";
            container.withEnv(prefix + "OWNER_PASSWORD", passwordOf(owner(service)))
                    .withEnv(prefix + "APP_PASSWORD", passwordOf(app(service)));
        }
        POSTGRES = container;
        POSTGRES.start();
    }

    private PostgresTestDatabase() {
    }

    /** Superusuário num banco de rascunho, para as peças do {@code common} testadas sem migration. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** O banco do serviço como na subida real: a aplicação conecta com a {@code _app} e o Flyway com a {@code _owner}. */
    public static void register(DynamicPropertyRegistry registry, String service) {
        String url = jdbcUrl(service);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> app(service));
        registry.add("spring.datasource.password", () -> passwordOf(app(service)));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> owner(service));
        registry.add("spring.flyway.password", () -> passwordOf(owner(service)));
    }

    public static String jdbcUrl(String service) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + requireService(service);
    }

    public static String owner(String service) {
        return requireService(service) + "_owner";
    }

    public static String app(String service) {
        return requireService(service) + "_app";
    }

    /** Conexão direta com uma das roles dos serviços no banco de um serviço, que pode ser o de outro. */
    public static Connection connectAs(String role, String service) throws SQLException {
        boolean known = SERVICES.stream().anyMatch(each -> role.equals(owner(each)) || role.equals(app(each)));
        if (!known) {
            throw new IllegalArgumentException("Role que o init.sh não cria: " + role);
        }
        return DriverManager.getConnection(jdbcUrl(service), role, passwordOf(role));
    }

    private static String passwordOf(String role) {
        return role + "-" + SECRET;
    }

    private static String requireService(String service) {
        if (!SERVICES.contains(service)) {
            throw new IllegalArgumentException("Serviço sem banco: " + service);
        }
        return service;
    }

    private static Path initScript() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path script = directory.resolve(Path.of("infra", "postgres", "init.sh"));
            if (Files.isRegularFile(script)) {
                return script;
            }
        }
        throw new IllegalStateException("infra/postgres/init.sh não encontrado acima de " + Path.of("").toAbsolutePath());
    }

    private static Set<String> servicesOf(Path script) {
        String content;
        try {
            content = Files.readString(script);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> services = SERVICE_CALL.matcher(content).results()
                .map(call -> call.group(1))
                .collect(Collectors.toUnmodifiableSet());
        if (services.isEmpty()) {
            throw new IllegalStateException("Nenhuma chamada a create_service_database em " + script);
        }
        return services;
    }

    private static String randomSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
