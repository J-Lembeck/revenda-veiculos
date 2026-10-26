package com.revenda.outboxprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.outbox.NewOutboxMessage;
import com.revenda.common.outbox.OutboxDispatcher;
import com.revenda.common.outbox.OutboxDispatcher.DispatchRun;
import com.revenda.common.outbox.OutboxMessage;
import com.revenda.common.outbox.OutboxPublisher;
import com.revenda.common.test.MovableClock;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.time.SqlTime;
import com.revenda.outboxprobe.OutboxProbeApplication.ProbeDestination;
import com.revenda.outboxprobe.OutboxProbeApplication.RecordingSender;
import com.revenda.outboxprobe.OutboxProbeApplication.ReleaseVehicle;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = OutboxProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:outbox-probe-schema.sql",
                "revenda.outbox.max-attempts=3"})
class OutboxTests {

    @Autowired
    private OutboxPublisher outbox;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient database;

    @Autowired
    private MovableClock clock;

    @Autowired
    @Qualifier("vehicleCommands")
    private RecordingSender vehicleCommands;

    @Autowired
    @Qualifier("paymentGateway")
    private RecordingSender paymentGateway;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void emptyTheQueue() {
        database.sql("DELETE FROM outbox_message").update();
        vehicleCommands.reset();
        paymentGateway.reset();
    }

    @Test
    void publishingOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> outbox.publish(release(UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(messages()).isZero();
    }

    @Test
    void messageExistsOnlyIfTheStateChangeCommits() {
        UUID purchaseId = UUID.randomUUID();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            database.sql("INSERT INTO probe_purchase (id, status) VALUES (:id, 'CANCELLED')")
                    .param("id", purchaseId).update();
            outbox.publish(release(UUID.randomUUID()));
            throw new IllegalStateException("a mudança falhou depois de publicar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(messages()).isZero();
        assertThat(database.sql("SELECT count(*) FROM probe_purchase WHERE id = :id")
                .param("id", purchaseId).query(Integer.class).single()).isZero();
    }

    @Test
    void dueMessageIsSentWithItsIdGroupAndPayloadAndMarkedDone() {
        UUID vehicleId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID id = publish(new NewOutboxMessage(ProbeDestination.VEHICLE_COMMANDS, "ReleaseVehicle", purchaseId,
                vehicleId.toString(), new ReleaseVehicle(vehicleId, purchaseId)));

        DispatchRun run = dispatcher.dispatchDue();

        assertThat(run).isEqualTo(new DispatchRun(1, 0, 0));
        assertThat(vehicleCommands.sent()).singleElement().satisfies(message -> {
            assertThat(message.id()).isEqualTo(id);
            assertThat(message.destination()).isEqualTo("VEHICLE_COMMANDS");
            assertThat(message.type()).isEqualTo("ReleaseVehicle");
            assertThat(message.aggregateId()).isEqualTo(purchaseId);
            assertThat(message.groupKey()).isEqualTo(vehicleId.toString());
            assertThat(message.payload()).contains(vehicleId.toString()).contains(purchaseId.toString());
            assertThat(message.attempts()).isZero();
            assertThat(message.createdAt()).isEqualTo(clock.instant());
        });
        assertThat(row(id)).containsEntry("status", "DONE").containsEntry("locked_until", null);
        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(0, 0, 0));
    }

    @Test
    void dispatchingInsideATransactionIsRefused() {
        publish(release(UUID.randomUUID()));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> dispatcher.dispatchDue()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(vehicleCommands.sent()).isEmpty();
    }

    @Test
    void failedSendWaitsTheBackoffAndIsTriedAgain() {
        UUID id = publish(release(UUID.randomUUID()));
        vehicleCommands.failNext(1);

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(0, 1, 0));
        Map<String, Object> retried = row(id);
        assertThat(retried).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error", "IllegalStateException");
        assertThat(((Timestamp) retried.get("available_at")).toInstant())
                .isEqualTo(clock.instant().plus(OutboxDispatcher.backoff(1)));

        clock.advance(OutboxDispatcher.backoff(1).minusMillis(1));
        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(0, 0, 0));
        clock.advance(Duration.ofMillis(1));
        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(1, 0, 0));

        assertThat(vehicleCommands.sent()).extracting(OutboxMessage::attempts).containsExactly(1);
        assertThat(row(id)).containsEntry("status", "DONE").containsEntry("last_error", null);
    }

    @Test
    void backoffDoublesUpToFiveMinutes() {
        assertThat(OutboxDispatcher.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxDispatcher.backoff(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(OutboxDispatcher.backoff(8)).isEqualTo(Duration.ofSeconds(256));
        assertThat(OutboxDispatcher.backoff(9)).isEqualTo(OutboxDispatcher.MAX_BACKOFF);
        assertThat(OutboxDispatcher.backoff(60)).isEqualTo(OutboxDispatcher.MAX_BACKOFF);
    }

    @Test
    void afterTheLastAttemptTheMessageFailsAndHoldsItsGroup() {
        UUID vehicleId = UUID.randomUUID();
        UUID first = publish(release(vehicleId));
        UUID second = publish(release(vehicleId));
        vehicleCommands.failNext(100);

        assertThat(dispatchAfterEachBackoff(3)).containsExactly(
                new DispatchRun(0, 1, 0), new DispatchRun(0, 1, 0), new DispatchRun(0, 0, 1));
        vehicleCommands.failNext(0);
        clock.advance(OutboxDispatcher.MAX_BACKOFF);

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(0, 0, 0));
        assertThat(row(first)).containsEntry("status", "FAILED").containsEntry("attempts", 3);
        assertThat(row(second)).containsEntry("status", "PENDING").containsEntry("attempts", 0);
        assertThat(vehicleCommands.sent()).isEmpty();
    }

    @Test
    void groupWaitsForItsOlderMessageWhileOtherGroupsGoAhead() {
        UUID vehicleId = UUID.randomUUID();
        UUID otherVehicleId = UUID.randomUUID();
        List<UUID> ids = transaction.execute(status -> List.of(
                outbox.publish(release(vehicleId)),
                outbox.publish(release(vehicleId)),
                outbox.publish(release(otherVehicleId))));
        vehicleCommands.failNext(1);

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(1, 1, 0));
        assertThat(vehicleCommands.sent()).extracting(OutboxMessage::id).containsExactly(ids.get(2));

        clock.advance(OutboxDispatcher.backoff(1));
        dispatcher.dispatchDue();
        dispatcher.dispatchDue();

        assertThat(vehicleCommands.sent()).extracting(OutboxMessage::id).containsExactly(ids.get(2), ids.get(0), ids.get(1));
    }

    @Test
    void sameGroupKeyInAnotherDestinationIsAnotherGroup() {
        String chargeId = UUID.randomUUID().toString();
        UUID purchaseId = UUID.randomUUID();
        publish(new NewOutboxMessage(ProbeDestination.VEHICLE_COMMANDS, "ReleaseVehicle", purchaseId, chargeId,
                new ReleaseVehicle(UUID.randomUUID(), purchaseId)));
        publish(new NewOutboxMessage(ProbeDestination.PAYMENT_GATEWAY, "CancelCharge", purchaseId, chargeId,
                new ReleaseVehicle(UUID.randomUUID(), purchaseId)));
        vehicleCommands.failNext(1);

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(1, 1, 0));
        assertThat(paymentGateway.sent()).extracting(OutboxMessage::type).containsExactly("CancelCharge");
    }

    @Test
    void destinationWithoutSenderIsRetriedAndNeverDropped() {
        UUID purchaseId = UUID.randomUUID();
        UUID id = publish(new NewOutboxMessage(ProbeDestination.DOCUMENTS, "GenerateDocument", purchaseId,
                purchaseId.toString(), new ReleaseVehicle(UUID.randomUUID(), purchaseId)));

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(0, 1, 0));

        assertThat(row(id)).containsEntry("status", "PENDING")
                .containsEntry("last_error", OutboxDispatcher.NO_SENDER);
    }

    @Test
    void expiredLeaseGoesBackToTheQueueCountingAnAttempt() {
        UUID id = publish(release(UUID.randomUUID()));
        database.sql("UPDATE outbox_message SET status = 'PROCESSING', locked_until = :past WHERE id = :id")
                .param("past", SqlTime.utc(clock.instant().minusSeconds(1)))
                .param("id", id).update();

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(1, 0, 0));

        assertThat(vehicleCommands.sent()).extracting(OutboxMessage::attempts).containsExactly(1);
        assertThat(row(id)).containsEntry("status", "DONE");
    }

    @Test
    void expiredLeaseOnTheLastAttemptFailsAndIsCountedInTheRun() {
        UUID id = publish(release(UUID.randomUUID()));
        database.sql("""
                        UPDATE outbox_message SET status = 'PROCESSING', attempts = 2, locked_until = :past
                         WHERE id = :id
                        """)
                .param("past", SqlTime.utc(clock.instant().minusSeconds(1)))
                .param("id", id).update();

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(0, 0, 1));

        assertThat(vehicleCommands.sent()).isEmpty();
        assertThat(row(id)).containsEntry("status", "FAILED").containsEntry("attempts", 3)
                .containsEntry("last_error", "LEASE_EXPIRED");
    }

    @Test
    void messageInSendByAnotherDispatcherIsNotTakenWhileItsLeaseLasts() {
        UUID id = publish(release(UUID.randomUUID()));
        database.sql("UPDATE outbox_message SET status = 'PROCESSING', locked_until = :future WHERE id = :id")
                .param("future", SqlTime.utc(clock.instant().plus(OutboxDispatcher.LEASE)))
                .param("id", id).update();

        assertThat(dispatcher.dispatchDue()).isEqualTo(new DispatchRun(0, 0, 0));
        assertThat(vehicleCommands.sent()).isEmpty();
    }

    @Test
    void concurrentDispatchersSendEachMessageOnce() throws Exception {
        List<UUID> ids = transaction.execute(status -> IntStream.range(0, 120)
                .mapToObj(index -> outbox.publish(release(UUID.randomUUID())))
                .toList());
        CountDownLatch start = new CountDownLatch(1);
        Callable<DispatchRun> dispatch = () -> {
            start.await();
            return dispatcher.dispatchDue();
        };

        List<Future<DispatchRun>> runs = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (int worker = 0; worker < 4; worker++) {
                runs.add(executor.submit(dispatch));
            }
            start.countDown();
        }

        int sent = 0;
        for (Future<DispatchRun> run : runs) {
            sent += run.get().sent();
        }
        assertThat(sent).isEqualTo(ids.size());
        Map<UUID, Long> timesSent = vehicleCommands.sent().stream()
                .collect(Collectors.groupingBy(OutboxMessage::id, Collectors.counting()));
        assertThat(timesSent).hasSize(ids.size()).allSatisfy((id, times) -> assertThat(times).isOne());
    }

    private List<DispatchRun> dispatchAfterEachBackoff(int runs) {
        List<DispatchRun> results = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            results.add(dispatcher.dispatchDue());
            clock.advance(OutboxDispatcher.backoff(run));
        }
        return results;
    }

    private UUID publish(NewOutboxMessage message) {
        return transaction.execute(status -> outbox.publish(message));
    }

    private Map<String, Object> row(UUID id) {
        return database.sql("""
                        SELECT status, attempts::int AS attempts, available_at, locked_until, last_error
                          FROM outbox_message WHERE id = :id
                        """)
                .param("id", id).query().singleRow();
    }

    private int messages() {
        return database.sql("SELECT count(*) FROM outbox_message").query(Integer.class).single();
    }

    private static NewOutboxMessage release(UUID vehicleId) {
        UUID purchaseId = UUID.randomUUID();
        return new NewOutboxMessage(ProbeDestination.VEHICLE_COMMANDS, "ReleaseVehicle", purchaseId,
                vehicleId.toString(), new ReleaseVehicle(vehicleId, purchaseId));
    }
}
