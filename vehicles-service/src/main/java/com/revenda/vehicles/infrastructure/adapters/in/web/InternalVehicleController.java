package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.ports.in.ReserveVehicleCommand;
import com.revenda.vehicles.domain.ports.in.ReserveVehicleUseCase;
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
@RestController
@RequestMapping("/internal/vehicles")
@RequiredArgsConstructor
class InternalVehicleController {

    private final ReserveVehicleUseCase reserveVehicle;

    @PostMapping("/{id}/reservations")
    ReservationResponse reserve(@PathVariable UUID id, @Valid @RequestBody ReservationRequest request) {
        return new ReservationResponse(
                reserveVehicle.reserve(new ReserveVehicleCommand(id, request.purchaseId())).toCents());
    }

    record ReservationRequest(@NotNull UUID purchaseId) {
    }

    record ReservationResponse(long priceCents) {
    }
}
