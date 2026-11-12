package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.sales.domain.ports.in.CreatePurchaseCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "O pedido de compra: só o veículo. O preço vem do estoque no momento da reserva; "
        + "qualquer valor no corpo é ignorado.")
record PurchaseRequest(
        @Schema(description = "Id do veículo escolhido na vitrine", example = "7c1e4b2a-9f3d-4e8a-b6c5-2d1f0a9e8b7c")
        @NotNull UUID vehicleId) {

    CreatePurchaseCommand toCommand(String customerSubject) {
        return new CreatePurchaseCommand(customerSubject, vehicleId);
    }
}
