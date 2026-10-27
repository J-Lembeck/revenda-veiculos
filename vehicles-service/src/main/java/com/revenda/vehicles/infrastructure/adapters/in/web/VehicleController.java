package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleCommand;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
class VehicleController {

    private final RegisterVehicleUseCase registerVehicle;

    @PostMapping
    ResponseEntity<StaffVehicleResponse> register(@Valid @RequestBody VehicleRequest request) {
        Vehicle vehicle = registerVehicle.register(new RegisterVehicleCommand(request.toDetails()));
        return ResponseEntity.created(URI.create("/vehicles/" + vehicle.id())).body(StaffVehicleResponse.of(vehicle));
    }
}
