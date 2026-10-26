package com.revenda.common.idempotency;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.CommonError;
import com.revenda.common.jdbc.Transactions;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.time.SqlTime;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * A chave fica em {@code idempotency_key}, no banco do serviço, sob o ator da requisição. A transação do
 * comando precisa estar aberta: quem perde a corrida pela mesma chave espera o commit de quem ganhou e
 * devolve o resultado dele; se quem ganhou desfaz, a chave some junto e quem esperava roda o comando.
 */
@RequiredArgsConstructor
public class JdbcIdempotencyStore implements IdempotencyStore {

    public static final Duration RETENTION = Duration.ofDays(7);

    private static final String CLAIM = """
            INSERT INTO idempotency_key (actor_id, key, scope, request_hash, created_at)
            VALUES (:actorId, :key, :scope, :requestHash, :createdAt)
            ON CONFLICT (actor_id, key) DO NOTHING
            """;
    private static final String FIND = """
            SELECT scope, request_hash, response_body::text AS response_body
              FROM idempotency_key
             WHERE actor_id = :actorId AND key = :key
            """;
    private static final String STORE = """
            UPDATE idempotency_key
               SET response_body = CAST(:body AS jsonb)
             WHERE actor_id = :actorId AND key = :key
            """;
    private static final String PURGE = "DELETE FROM idempotency_key WHERE created_at < :cutoff";

    private final DataSource dataSource;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public <T> Outcome<T> once(Class<T> resultType, Supplier<T> command) {
        Transactions.requireOpen(dataSource, "A chave de idempotência só é gravada dentro da transação do comando");
        IdempotencyRequest request = IdempotencyContextHolder.get();
        if (request == null) {
            return new Outcome<>(command.get(), false);
        }
        if (request.isAbsent()) {
            throw BusinessException.of(CommonError.IDEMPOTENCY_KEY_REQUIRED);
        }

        String actorId = requireActor();
        if (claim(actorId, request)) {
            T result = command.get();
            store(actorId, request.key(), result);
            return new Outcome<>(result, false);
        }
        String storedResult = find(actorId, request.key())
                .filter(stored -> stored.matches(request))
                .map(Stored::responseBody)
                .orElseThrow(() -> BusinessException.of(CommonError.IDEMPOTENCY_KEY_REUSED));
        return new Outcome<>(objectMapper.readValue(storedResult, resultType), true);
    }

    @Override
    public int purgeExpired() {
        return jdbcClient.sql(PURGE)
                .param("cutoff", SqlTime.utc(clock.instant().minus(RETENTION)))
                .update();
    }

    private boolean claim(String actorId, IdempotencyRequest request) {
        return jdbcClient.sql(CLAIM)
                .param("actorId", actorId)
                .param("key", request.key())
                .param("scope", request.scope())
                .param("requestHash", request.requestHash())
                .param("createdAt", SqlTime.utc(clock.instant()))
                .update() == 1;
    }

    private Optional<Stored> find(String actorId, String key) {
        return jdbcClient.sql(FIND)
                .param("actorId", actorId)
                .param("key", key)
                .query((row, index) -> new Stored(
                        row.getString("scope"), row.getString("request_hash"), row.getString("response_body")))
                .optional();
    }

    private void store(String actorId, String key, Object result) {
        jdbcClient.sql(STORE)
                .param("actorId", actorId)
                .param("key", key)
                .param("body", objectMapper.writeValueAsString(result))
                .update();
    }

    private static String requireActor() {
        String actorId = ActorContextHolder.get().id();
        if (actorId == null) {
            throw new IllegalStateException("A chave de idempotência pertence a um ator identificado");
        }
        return actorId;
    }

    private record Stored(String scope, String requestHash, String responseBody) {

        boolean matches(IdempotencyRequest request) {
            return responseBody != null
                    && scope.equals(request.scope())
                    && requestHash.equals(request.requestHash());
        }
    }
}
