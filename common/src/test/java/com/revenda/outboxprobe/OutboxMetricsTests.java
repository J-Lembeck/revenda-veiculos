package com.revenda.outboxprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.jdbc.Transactions;
import com.revenda.common.outbox.NewOutboxMessage;
import com.revenda.common.outbox.OutboxPublisher;
import com.revenda.common.test.MovableClock;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.outboxprobe.OutboxProbeApplication.ProbeDestination;
import com.revenda.outboxprobe.OutboxProbeApplication.ReleaseVehicle;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = OutboxProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:outbox-probe-schema.sql",
                "revenda.outbox.max-attempts=3"})
class OutboxMetricsTests {

    private static final Duration PAST_THE_CACHE = Duration.ofSeconds(15);

    @Autowired
    private List<MeterBinder> binders;

    @Autowired
    private OutboxPublisher outbox;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient database;

    @Autowired
    private MovableClock clock;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void emptyTheQueueAndBind() {
        database.sql("DELETE FROM outbox_message").update();
        binders.forEach(binder -> binder.bindTo(registry));
        clock.advance(PAST_THE_CACHE);
    }

    @Test
    void eachDestinationOfTheServiceHasBothMeasures() {
        for (String destination : List.of("VEHICLE_COMMANDS", "PAYMENT_GATEWAY")) {
            assertThat(failed(destination)).isZero();
            assertThat(oldestAgeSeconds(destination)).isZero();
        }
    }

    @Test
    void theAgeIsThatOfTheOldestMessageNotYetSent() {
        publish(ProbeDestination.VEHICLE_COMMANDS);
        clock.advance(Duration.ofSeconds(60));
        publish(ProbeDestination.VEHICLE_COMMANDS);
        clock.advance(Duration.ofSeconds(30));

        assertThat(oldestAgeSeconds("VEHICLE_COMMANDS")).isEqualTo(90.0);
        assertThat(oldestAgeSeconds("PAYMENT_GATEWAY")).isZero();
    }

    @Test
    void sentAndFailedMessagesAreNotWaiting() {
        UUID sent = publish(ProbeDestination.VEHICLE_COMMANDS);
        UUID failed = publish(ProbeDestination.VEHICLE_COMMANDS);
        clock.advance(Duration.ofSeconds(40));
        setStatus(sent, "DONE");
        setStatus(failed, "FAILED");

        assertThat(oldestAgeSeconds("VEHICLE_COMMANDS")).isZero();
    }

    @Test
    void failedMessagesAreCountedPerDestination() {
        setStatus(publish(ProbeDestination.PAYMENT_GATEWAY), "FAILED");
        setStatus(publish(ProbeDestination.PAYMENT_GATEWAY), "FAILED");
        setStatus(publish(ProbeDestination.PAYMENT_GATEWAY), "DONE");
        publish(ProbeDestination.PAYMENT_GATEWAY);

        assertThat(failed("PAYMENT_GATEWAY")).isEqualTo(2.0);
        assertThat(failed("VEHICLE_COMMANDS")).isZero();
    }

    @Test
    void theDatabaseIsReadAtMostOnceEvery15Seconds() {
        assertThat(failed("PAYMENT_GATEWAY")).isZero();
        setStatus(publish(ProbeDestination.PAYMENT_GATEWAY), "FAILED");

        clock.advance(Duration.ofSeconds(14));
        assertThat(failed("PAYMENT_GATEWAY")).isZero();

        clock.advance(Duration.ofSeconds(1));
        assertThat(failed("PAYMENT_GATEWAY")).isEqualTo(1.0);
    }

    @Test
    void afterCommitRunsOnlyWhenTheTransactionCommits() {
        AtomicInteger runs = new AtomicInteger();

        transaction.executeWithoutResult(status -> {
            Transactions.afterCommit(runs::incrementAndGet);
            assertThat(runs).hasValue(0);
        });
        assertThat(runs).hasValue(1);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Transactions.afterCommit(runs::incrementAndGet);
            throw new IllegalStateException("a mudança falhou");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(runs).hasValue(1);

        Transactions.afterCommit(runs::incrementAndGet);
        assertThat(runs).hasValue(2);
    }

    private double failed(String destination) {
        return registry.get("outbox.messages.failed").tag("destination", destination).gauge().value();
    }

    private double oldestAgeSeconds(String destination) {
        return registry.get("outbox.messages.oldest.age").tag("destination", destination).timeGauge()
                .value(TimeUnit.SECONDS);
    }

    private UUID publish(ProbeDestination destination) {
        UUID vehicleId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        return transaction.execute(status -> outbox.publish(new NewOutboxMessage(destination, "ReleaseVehicle",
                purchaseId, vehicleId.toString(), new ReleaseVehicle(vehicleId, purchaseId))));
    }

    private void setStatus(UUID id, String status) {
        database.sql("UPDATE outbox_message SET status = :status WHERE id = :id")
                .param("status", status).param("id", id).update();
    }
}
