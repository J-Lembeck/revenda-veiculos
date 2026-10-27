package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.ports.in.EditVehicleCommand;
import com.revenda.vehicles.domain.ports.in.EditVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleCommand;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleUseCase;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
class VehicleController {

    private final RegisterVehicleUseCase registerVehicle;
    private final EditVehicleUseCase editVehicle;

    @PostMapping
    ResponseEntity<StaffVehicleResponse> register(@Valid @RequestBody VehicleRequest request) {
        Vehicle vehicle = registerVehicle.register(new RegisterVehicleCommand(request.toDetails()));
        return ResponseEntity.created(URI.create("/vehicles/" + vehicle.id())).body(StaffVehicleResponse.of(vehicle));
    }

    @PutMapping("/{id}")
    StaffVehicleResponse edit(@PathVariable UUID id,
            @Validated({Default.class, VehicleRequest.Edit.class}) @RequestBody VehicleRequest request) {
        return StaffVehicleResponse.of(
                editVehicle.edit(new EditVehicleCommand(id, request.toDetails(), request.version())));
    }
}
