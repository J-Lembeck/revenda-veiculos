package com.revenda.sales.domain.ports.in;

import java.util.Objects;
import java.util.UUID;

/** O pedido do cliente: só o veículo. O preço vem da reserva (RN-09); o cliente, do token. */
public record CreatePurchaseCommand(String customerSubject, UUID vehicleId) {

    public CreatePurchaseCommand {
        Objects.requireNonNull(customerSubject, "customerSubject");
        Objects.requireNonNull(vehicleId, "vehicleId");
    }

    @Override
    public String toString() {
        return "CreatePurchaseCommand[vehicleId=" + vehicleId + "]";
    }
}
