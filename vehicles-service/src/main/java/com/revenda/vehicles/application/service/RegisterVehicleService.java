package com.revenda.vehicles.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.idempotency.IdempotencyStore;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleCommand;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleUseCase;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterVehicleService implements RegisterVehicleUseCase {

    private final SaveVehiclePort vehicles;
    private final IdempotencyStore idempotency;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public Vehicle register(RegisterVehicleCommand command) {
        return idempotency.once(Vehicle.class, () -> {
            Vehicle vehicle = Vehicle.register(UUID.randomUUID(), command.vehicle(), clock.instant());
            vehicles.insert(vehicle);
            audit.record(AuditEntry.of(VehiclesAuditAction.VEHICLE_CREATED, Vehicle.ENTITY_TYPE, vehicle.id(),
                            summary(vehicle.details()))
                    .withChange(null, VehicleSnapshot.of(vehicle)));
            return vehicle;
        }).result();
    }

    private static String summary(VehicleDetails details) {
        return "Veículo cadastrado: " + details.brand() + " " + details.model() + " "
                + details.manufactureYear() + "/" + details.modelYear();
    }
}
