package com.revenda.idempotencyprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.error.BusinessException;
import com.revenda.common.idempotency.IdempotencyContextHolder;
import com.revenda.common.idempotency.IdempotencyRequest;
import com.revenda.common.idempotency.IdempotencyStore;
import com.revenda.common.idempotency.IdempotencyStore.Outcome;
import com.revenda.common.idempotency.JdbcIdempotencyStore;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.common.time.SqlTime;
import com.revenda.idempotencyprobe.IdempotencyProbeApplication.RegisteredVehicle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class JdbcIdempotencyStoreTests extends IdempotencyProbeSupport {

    private static final String SCOPE = "POST /vehicles";
    private static final String SELLER = "6a0f3c1e-vendedora";
    private static final String OTHER_SELLER = "9d2b7e4a-vendedor";

    @Autowired
    private IdempotencyStore store;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient database;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @AfterEach
    void clearContext() {
        IdempotencyContextHolder.clear();
    }

    @Test
    void refusesToRunOutsideATransaction() {
        IdempotencyContextHolder.set(request(newKey(), "a"));

        assertThatThrownBy(() -> ActorContextHolder.callAs(seller(SELLER),
                () -> store.once(RegisteredVehicle.class, () -> vehicle("fora"))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void commandRunsOnceAndTheRepetitionGetsTheStoredResult() {
        String key = newKey();
        AtomicInteger runs = new AtomicInteger();
        RegisteredVehicle original = vehicle("ABC1D23");

        Outcome<RegisteredVehicle> first = run(SELLER, request(key, "a"), counting(runs, original));
        Outcome<RegisteredVehicle> second = run(SELLER, request(key, "a"), counting(runs, vehicle("XYZ9W87")));

        assertThat(runs).hasValue(1);
        assertThat(first).isEqualTo(new Outcome<>(original, false));
        assertThat(second).isEqualTo(new Outcome<>(original, true));
    }

    @Test
    void sameKeyWithAnotherRequestIsRefusedAsReused() {
        String key = newKey();
        run(SELLER, request(key, "a"), () -> vehicle("ABC1D23"));

        assertThatThrownBy(() -> run(SELLER, request(key, "b"), () -> vehicle("outro corpo")))
                .isInstanceOfSatisfying(BusinessException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        assertThatThrownBy(() -> run(SELLER, new IdempotencyRequest(key, "POST /purchases", "a"),
                () -> vehicle("outra rota")))
                .isInstanceOfSatisfying(BusinessException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void commandThatFailsLeavesNoKeyBehind() {
        String key = newKey();
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> run(SELLER, request(key, "a"), () -> {
            runs.incrementAndGet();
            throw new IllegalStateException("o comando falhou");
        })).isInstanceOf(IllegalStateException.class);
        Outcome<RegisteredVehicle> retried = run(SELLER, request(key, "a"), counting(runs, vehicle("agora foi")));

        assertThat(runs).hasValue(2);
        assertThat(retried.replayed()).isFalse();
        assertThat(keysNamed(key)).isOne();
    }

    @Test
    void postWithoutTheKeyIsRefusedAndCallersOutsideHttpRunStraight() {
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> run(SELLER, IdempotencyRequest.absent(), counting(runs, vehicle("sem chave"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED"));
        IdempotencyContextHolder.clear();
        transaction.execute(status -> store.once(RegisteredVehicle.class, counting(runs, vehicle("job"))));
        transaction.execute(status -> store.once(RegisteredVehicle.class, counting(runs, vehicle("job"))));

        assertThat(runs).hasValue(2);
    }

    @Test
    void theSameKeyFromAnotherActorIsAnotherRequest() {
        String key = newKey();
        AtomicInteger runs = new AtomicInteger();
        RegisteredVehicle fromFirst = vehicle("ABC1D23");

        run(SELLER, request(key, "a"), counting(runs, fromFirst));
        Outcome<RegisteredVehicle> fromSecond = run(OTHER_SELLER, request(key, "a"), counting(runs, vehicle("XYZ9W87")));
        Outcome<RegisteredVehicle> repeated = run(SELLER, request(key, "a"), counting(runs, vehicle("de novo")));

        assertThat(runs).hasValue(2);
        assertThat(fromSecond.replayed()).isFalse();
        assertThat(repeated).isEqualTo(new Outcome<>(fromFirst, true));
        assertThat(database.sql("SELECT actor_id FROM idempotency_key WHERE key = :key")
                .param("key", key).query(String.class).list())
                .containsExactlyInAnyOrder(SELLER, OTHER_SELLER);
    }

    @Test
    void keyBelongsToAnIdentifiedActor() {
        IdempotencyContextHolder.set(request(newKey(), "a"));

        assertThatThrownBy(() -> ActorContextHolder.callAs(ActorContext.anonymous("req-anonima"),
                () -> transaction.execute(status -> store.once(RegisteredVehicle.class, () -> vehicle("anônimo")))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void storedRowKeepsTheRouteTheSummaryAndOnlyTheResult() {
        String key = newKey();
        RegisteredVehicle result = vehicle("ABC1D23");

        run(SELLER, request(key, "resumo"), () -> result);

        Map<String, Object> row = database.sql("""
                        SELECT scope, request_hash, response_body::text AS response_body
                          FROM idempotency_key WHERE actor_id = :actorId AND key = :key
                        """)
                .param("actorId", SELLER).param("key", key).query().singleRow();
        assertThat(row).containsEntry("scope", SCOPE).containsEntry("request_hash", "resumo");
        assertThat(objectMapper.readValue((String) row.get("response_body"), RegisteredVehicle.class)).isEqualTo(result);
    }

    @Test
    void whoLosesTheRaceWaitsAndGetsTheWinnersResult() throws Exception {
        String key = newKey();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch winnerInside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RegisteredVehicle winners = vehicle("de quem chegou primeiro");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome<RegisteredVehicle>> winner = executor.submit(() -> inOwnThread(() ->
                    run(SELLER, request(key, "a"), () -> {
                        runs.incrementAndGet();
                        winnerInside.countDown();
                        await(release);
                        return winners;
                    })));
            assertThat(winnerInside.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Outcome<RegisteredVehicle>> loser = executor.submit(() -> inOwnThread(() ->
                    run(SELLER, request(key, "a"), counting(runs, vehicle("de quem chegou depois")))));
            assertThatThrownBy(() -> loser.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();

            assertThat(winner.get(10, TimeUnit.SECONDS)).isEqualTo(new Outcome<>(winners, false));
            assertThat(loser.get(10, TimeUnit.SECONDS)).isEqualTo(new Outcome<>(winners, true));
        }
        assertThat(runs).hasValue(1);
    }

    @Test
    void purgeRemovesOnlyTheKeysOlderThanTheRetention() {
        String expired = newKey();
        String recent = newKey();
        Instant now = clock.instant();
        insertKey(expired, now.minus(JdbcIdempotencyStore.RETENTION).minus(Duration.ofMinutes(1)));
        insertKey(recent, now.minus(JdbcIdempotencyStore.RETENTION).plus(Duration.ofHours(1)));

        assertThat(store.purgeExpired()).isPositive();

        assertThat(keysNamed(expired)).isZero();
        assertThat(keysNamed(recent)).isOne();
    }

    private Outcome<RegisteredVehicle> run(String actorId, IdempotencyRequest request,
            Supplier<RegisteredVehicle> command) {
        IdempotencyContextHolder.set(request);
        return ActorContextHolder.callAs(seller(actorId),
                () -> transaction.execute(status -> store.once(RegisteredVehicle.class, command)));
    }

    private static <T> T inOwnThread(Supplier<T> work) {
        try {
            return work.get();
        } finally {
            IdempotencyContextHolder.clear();
        }
    }

    private void insertKey(String key, Instant createdAt) {
        database.sql("""
                        INSERT INTO idempotency_key (actor_id, key, scope, request_hash, response_body, created_at)
                        VALUES (:actorId, :key, :scope, 'a', '{}', :createdAt)
                        """)
                .param("actorId", SELLER)
                .param("key", key)
                .param("scope", SCOPE)
                .param("createdAt", SqlTime.utc(createdAt))
                .update();
    }

    private int keysNamed(String key) {
        return database.sql("SELECT count(*) FROM idempotency_key WHERE key = :key")
                .param("key", key).query(Integer.class).single();
    }

    private static Supplier<RegisteredVehicle> counting(AtomicInteger runs, RegisteredVehicle result) {
        return () -> {
            runs.incrementAndGet();
            return result;
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("ninguém liberou o comando");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static ActorContext seller(String actorId) {
        return new ActorContext(ActorType.USER, actorId, Role.SELLER, "req-" + actorId);
    }

    private static IdempotencyRequest request(String key, String requestHash) {
        return new IdempotencyRequest(key, SCOPE, requestHash);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static RegisteredVehicle vehicle(String plate) {
        return new RegisteredVehicle(UUID.randomUUID(), plate, 8990000, Instant.parse("2026-10-26T14:32:00Z"));
    }
}
