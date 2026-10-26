package com.revenda.outboxprobe;

import com.revenda.common.outbox.OutboxDestination;
import com.revenda.common.outbox.OutboxMessage;
import com.revenda.common.outbox.OutboxSender;
import com.revenda.common.test.MovableClock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
class OutboxProbeApplication {

    static final Instant START = Instant.parse("2026-10-26T14:32:00Z");

    @Bean
    MovableClock clock() {
        return new MovableClock(START);
    }

    @Bean
    RecordingSender vehicleCommands() {
        return new RecordingSender(ProbeDestination.VEHICLE_COMMANDS);
    }

    @Bean
    RecordingSender paymentGateway() {
        return new RecordingSender(ProbeDestination.PAYMENT_GATEWAY);
    }

    enum ProbeDestination implements OutboxDestination {
        VEHICLE_COMMANDS,
        PAYMENT_GATEWAY,
        DOCUMENTS
    }

    record ReleaseVehicle(UUID vehicleId, UUID purchaseId) {
    }

    static final class RecordingSender implements OutboxSender {

        private final ProbeDestination destination;
        private final List<OutboxMessage> sent = new CopyOnWriteArrayList<>();
        private final AtomicInteger failuresLeft = new AtomicInteger();

        RecordingSender(ProbeDestination destination) {
            this.destination = destination;
        }

        @Override
        public OutboxDestination destination() {
            return destination;
        }

        @Override
        public void send(OutboxMessage message) {
            if (failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                throw new IllegalStateException("fila fora do ar");
            }
            sent.add(message);
        }

        void failNext(int times) {
            failuresLeft.set(times);
        }

        List<OutboxMessage> sent() {
            return sent;
        }

        void reset() {
            sent.clear();
            failuresLeft.set(0);
        }
    }
}
