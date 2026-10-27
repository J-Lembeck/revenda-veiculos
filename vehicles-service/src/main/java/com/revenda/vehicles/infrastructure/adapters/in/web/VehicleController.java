package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.ports.in.EditVehicleCommand;
import com.revenda.vehicles.domain.ports.in.EditVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.ListSoldVehiclesUseCase;
import com.revenda.vehicles.domain.ports.in.ListVehiclesForSaleUseCase;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleCommand;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.ViewVehicleUseCase;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final ListVehiclesForSaleUseCase listVehiclesForSale;
    private final ViewVehicleUseCase viewVehicle;
    private final ListSoldVehiclesUseCase listSoldVehicles;

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

    @GetMapping
    PageResponse<PublicVehicleResponse> listForSale(@Valid ShowcaseParams params) {
        return PageResponse.of(listVehiclesForSale.list(params.toFilter(), params.toPaging()),
                PublicVehicleResponse::of);
    }

    @GetMapping("/sold")
    PageResponse<StaffVehicleResponse> listSold(@Valid PageParams params) {
        return PageResponse.of(listSoldVehicles.list(params.toPaging()), StaffVehicleResponse::of);
    }

    @GetMapping("/{id}")
    Object view(@PathVariable UUID id) {
        boolean staff = isStaff(ActorContextHolder.get());
        Vehicle vehicle = viewVehicle.view(id, staff);
        return staff ? StaffVehicleResponse.of(vehicle) : PublicVehicleResponse.of(vehicle);
    }

    private static boolean isStaff(ActorContext actor) {
        return actor.type() == ActorType.USER && (actor.role() == Role.SELLER || actor.role() == Role.ADMIN);
    }
}
