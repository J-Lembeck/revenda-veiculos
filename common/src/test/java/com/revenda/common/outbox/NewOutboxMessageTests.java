package com.revenda.common.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewOutboxMessageTests {

    private static final UUID PURCHASE_ID = UUID.fromString("4c2a9e17-6b3d-4f08-a1e5-7d9b3c5f1a2e");

    private enum Destination implements OutboxDestination {
        VEHICLE_COMMANDS,
        DESTINATION_NAME_TOO_LONG
    }

    private record Command(UUID purchaseId) {
    }

    @Test
    void acceptsTheLimitsOfTheTable() {
        assertThatCode(() -> new NewOutboxMessage(Destination.VEHICLE_COMMANDS, "R".repeat(NewOutboxMessage.TYPE_LIMIT),
                PURCHASE_ID, "g".repeat(NewOutboxMessage.GROUP_KEY_LIMIT), new Command(PURCHASE_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesWhatTheTableCannotHold() {
        assertThatThrownBy(() -> message(Destination.DESTINATION_NAME_TOO_LONG, "ReleaseVehicle", "grupo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> message(Destination.VEHICLE_COMMANDS, " ", "grupo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> message(Destination.VEHICLE_COMMANDS, "R".repeat(NewOutboxMessage.TYPE_LIMIT + 1),
                "grupo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> message(Destination.VEHICLE_COMMANDS, "ReleaseVehicle", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> message(Destination.VEHICLE_COMMANDS, "ReleaseVehicle",
                "g".repeat(NewOutboxMessage.GROUP_KEY_LIMIT + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyPartIsRequired() {
        Command payload = new Command(PURCHASE_ID);

        assertThatThrownBy(() -> new NewOutboxMessage(null, "ReleaseVehicle", PURCHASE_ID, "grupo", payload))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NewOutboxMessage(Destination.VEHICLE_COMMANDS, null, PURCHASE_ID, "grupo",
                payload))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NewOutboxMessage(Destination.VEHICLE_COMMANDS, "ReleaseVehicle", null, "grupo",
                payload))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NewOutboxMessage(Destination.VEHICLE_COMMANDS, "ReleaseVehicle", PURCHASE_ID,
                null, payload))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NewOutboxMessage(Destination.VEHICLE_COMMANDS, "ReleaseVehicle", PURCHASE_ID,
                "grupo", null))
                .isInstanceOf(NullPointerException.class);
    }

    private static NewOutboxMessage message(OutboxDestination destination, String type, String groupKey) {
        return new NewOutboxMessage(destination, type, PURCHASE_ID, groupKey, new Command(PURCHASE_ID));
    }
}
