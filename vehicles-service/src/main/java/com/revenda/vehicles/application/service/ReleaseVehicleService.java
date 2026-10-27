package com.revenda.vehicles.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.in.ReleaseVehicleCommand;
import com.revenda.vehicles.domain.ports.in.ReleaseVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.VehicleCommandOutcome;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import com.revenda.vehicles.domain.ports.out.SaveProcessedMessagePort;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReleaseVehicleService implements ReleaseVehicleUseCase {

    private final SaveProcessedMessagePort processed;
    private final SaveVehiclePort vehicles;
    private final LoadVehiclePort stock;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public VehicleCommandOutcome release(ReleaseVehicleCommand command) {
        Instant now = clock.instant();
        if (!processed.markProcessed(command.messageId(), SaveProcessedMessagePort.VEHICLE_COMMANDS, now)) {
            return VehicleCommandOutcome.DUPLICATE;
        }
        if (vehicles.releaseIfHeldBy(command.vehicleId(), command.purchaseId(), now)) {
            audit.record(AuditEntry.of(VehiclesAuditAction.VEHICLE_RELEASED, Vehicle.ENTITY_TYPE, command.vehicleId(),
                            "Veículo liberado da compra " + command.purchaseId())
                    .withExtra(new Release(command.messageId(), command.purchaseId())));
            return VehicleCommandOutcome.APPLIED;
        }
        boolean delivered = stock.findById(command.vehicleId())
                .filter(vehicle -> vehicle.isDeliveredTo(command.purchaseId()))
                .isPresent();
        if (delivered) {
            throw BusinessException.of(VehiclesError.VEHICLE_COMMAND_CONTRADICTORY);
        }
        return VehicleCommandOutcome.NO_EFFECT;
    }

    private record Release(UUID messageId, UUID purchaseId) {
    }
}
