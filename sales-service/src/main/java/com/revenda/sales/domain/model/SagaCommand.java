package com.revenda.sales.domain.model;

import com.revenda.common.money.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um comando que a saga manda a outro participante depois de uma mudança da compra, pelo outbox. Os comandos do
 * estoque saem em ordem por veículo; os do gateway, em ordem por compra — uma compra tem uma cobrança, e o
 * cancelamento pode sair antes de se saber o id dela.
 */
public sealed interface SagaCommand {

    UUID purchaseId();

    CommandDestination destination();

    String type();

    String groupKey();

    record ReleaseVehicle(UUID purchaseId, UUID vehicleId) implements SagaCommand {

        public static final String TYPE = "ReleaseVehicle";

        public ReleaseVehicle {
            Objects.requireNonNull(purchaseId, "purchaseId");
            Objects.requireNonNull(vehicleId, "vehicleId");
        }

        @Override
        public CommandDestination destination() {
            return CommandDestination.VEHICLE_COMMANDS;
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String groupKey() {
            return vehicleId.toString();
        }
    }

    record MarkVehicleSold(UUID purchaseId, UUID vehicleId, Money soldPrice, Instant soldAt) implements SagaCommand {

        public static final String TYPE = "MarkVehicleSold";

        public MarkVehicleSold {
            Objects.requireNonNull(purchaseId, "purchaseId");
            Objects.requireNonNull(vehicleId, "vehicleId");
            Objects.requireNonNull(soldPrice, "soldPrice");
            Objects.requireNonNull(soldAt, "soldAt");
        }

        @Override
        public CommandDestination destination() {
            return CommandDestination.VEHICLE_COMMANDS;
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String groupKey() {
            return vehicleId.toString();
        }
    }

    record DeliverVehicle(UUID purchaseId, UUID vehicleId, Instant deliveredAt) implements SagaCommand {

        public static final String TYPE = "DeliverVehicle";

        public DeliverVehicle {
            Objects.requireNonNull(purchaseId, "purchaseId");
            Objects.requireNonNull(vehicleId, "vehicleId");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }

        @Override
        public CommandDestination destination() {
            return CommandDestination.VEHICLE_COMMANDS;
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String groupKey() {
            return vehicleId.toString();
        }
    }

    /** Cancela a cobrança da compra; sem {@code chargeId}, o gateway a procura pela compra, se ela chegou a existir. */
    record CancelCharge(UUID purchaseId, String chargeId) implements SagaCommand {

        public static final String TYPE = "CancelCharge";

        public CancelCharge {
            Objects.requireNonNull(purchaseId, "purchaseId");
        }

        @Override
        public CommandDestination destination() {
            return CommandDestination.PAYMENT_GATEWAY;
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String groupKey() {
            return purchaseId.toString();
        }
    }

    /** Estorna o que foi pago na cobrança da compra. */
    record RefundCharge(UUID purchaseId, String chargeId) implements SagaCommand {

        public static final String TYPE = "RefundCharge";

        public RefundCharge {
            Objects.requireNonNull(purchaseId, "purchaseId");
            Objects.requireNonNull(chargeId, "chargeId");
        }

        @Override
        public CommandDestination destination() {
            return CommandDestination.PAYMENT_GATEWAY;
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String groupKey() {
            return purchaseId.toString();
        }
    }
}
