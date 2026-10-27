package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.ports.in.ReserveVehicleCommand;
import com.revenda.vehicles.domain.ports.in.ReserveVehicleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O que só o {@code sales} chama, com token de serviço: a reserva do passo 2 da compra. */
@Tag(name = "Interno", description = "Rotas que só o serviço de vendas chama, com token de serviço; não são "
        + "publicadas no API Gateway.")
@RestController
@RequestMapping("/internal/vehicles")
@RequiredArgsConstructor
class InternalVehicleController {

    private final ReserveVehicleUseCase reserveVehicle;

    @Operation(summary = "Reservar veículo para uma compra", description = "Passo 2 da compra. Um único `UPDATE` "
            + "condicional (`WHERE status = 'AVAILABLE'`): de N reservas simultâneas, exatamente uma vence (RN-08). "
            + "Devolve o preço do momento, que a compra vai cobrar (RN-09). Repetir com a mesma compra enquanto o "
            + "veículo segue reservado por ela devolve `200` sem efeito — a idempotência é pelo `purchaseId`, sem "
            + "`Idempotency-Key`. Grava `VEHICLE_RESERVED` com o serviço, a compra e o preço.")
    @ApiResponse(responseCode = "200", description = "Reservado para a compra (ou já estava).")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` — `purchaseId` `REQUIRED` ou "
            + "`TYPE_INVALID`.")
    @ApiResponse(responseCode = "409", description = "`VEHICLE_UNAVAILABLE` — reservado por outra compra, vendido, "
            + "entregue ou inexistente.")
    @PostMapping("/{id}/reservations")
    ReservationResponse reserve(@Parameter(description = "Id do veículo") @PathVariable UUID id,
            @Valid @RequestBody ReservationRequest request) {
        return new ReservationResponse(
                reserveVehicle.reserve(new ReserveVehicleCommand(id, request.purchaseId())).toCents());
    }

    record ReservationRequest(
            @Schema(description = "Id da compra que reserva", example = "0b7e4c1a-5d2f-4e8b-9c3a-7f1e2d4b6a90")
            @NotNull UUID purchaseId) {
    }

    record ReservationResponse(
            @Schema(description = "Preço do veículo no momento da reserva, em centavos", example = "8990000")
            long priceCents) {
    }
}
