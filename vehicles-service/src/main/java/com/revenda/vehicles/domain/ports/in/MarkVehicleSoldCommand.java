package com.revenda.vehicles.domain.ports.in;

import com.revenda.common.money.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** {@code soldPrice} é o preço fixado na reserva (RN-09); {@code soldAt}, quando o pagamento foi confirmado. */
public record MarkVehicleSoldCommand(UUID messageId, UUID vehicleId, UUID purchaseId, Money soldPrice,
        Instant soldAt) {

    public MarkVehicleSoldCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(soldPrice, "soldPrice");
        Objects.requireNonNull(soldAt, "soldAt");
        if (soldPrice.toCents() <= 0) {
            throw new IllegalArgumentException("Preço de venda precisa ser positivo");
        }
    }
}
