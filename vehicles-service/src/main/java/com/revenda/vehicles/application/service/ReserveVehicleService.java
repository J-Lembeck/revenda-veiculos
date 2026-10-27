package com.revenda.vehicles.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.in.ReserveVehicleCommand;
import com.revenda.vehicles.domain.ports.in.ReserveVehicleUseCase;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReserveVehicleService implements ReserveVehicleUseCase {

    private final SaveVehiclePort vehicles;
    private final LoadVehiclePort stock;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public Money reserve(ReserveVehicleCommand command) {
        Optional<Money> reserved = vehicles.reserveIfAvailable(command.vehicleId(), command.purchaseId(),
                clock.instant());
        if (reserved.isPresent()) {
            audit.record(AuditEntry.of(VehiclesAuditAction.VEHICLE_RESERVED, Vehicle.ENTITY_TYPE, command.vehicleId(),
                            "Veículo reservado para a compra " + command.purchaseId())
                    .withExtra(new Reservation(command.purchaseId(), reserved.get().toCents())));
            return reserved.get();
        }
        return stock.findById(command.vehicleId())
                .filter(vehicle -> vehicle.isReservedFor(command.purchaseId()))
                .map(vehicle -> vehicle.details().price())
                .orElseThrow(() -> BusinessException.of(VehiclesError.VEHICLE_UNAVAILABLE));
    }

    private record Reservation(UUID purchaseId, long priceCents) {
    }
}
