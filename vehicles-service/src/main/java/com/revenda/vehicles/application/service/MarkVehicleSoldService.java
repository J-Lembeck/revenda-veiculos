package com.revenda.vehicles.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldCommand;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldUseCase;
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
public class MarkVehicleSoldService implements MarkVehicleSoldUseCase {

    private final SaveProcessedMessagePort processed;
    private final SaveVehiclePort vehicles;
    private final LoadVehiclePort stock;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public VehicleCommandOutcome markSold(MarkVehicleSoldCommand command) {
        Instant now = clock.instant();
        if (!processed.markProcessed(command.messageId(), SaveProcessedMessagePort.VEHICLE_COMMANDS, now)) {
            return VehicleCommandOutcome.DUPLICATE;
        }
        if (vehicles.markSoldIfReservedBy(command.vehicleId(), command.purchaseId(), command.soldPrice(),
                command.soldAt(), now)) {
            audit.record(AuditEntry.of(VehiclesAuditAction.VEHICLE_SOLD, Vehicle.ENTITY_TYPE, command.vehicleId(),
                            "Veículo vendido na compra " + command.purchaseId())
                    .withExtra(new Sale(command.messageId(), command.purchaseId(), command.soldPrice().toCents(),
                            command.soldAt())));
            return VehicleCommandOutcome.APPLIED;
        }
        boolean alreadySold = stock.findById(command.vehicleId())
                .filter(vehicle -> vehicle.isSoldTo(command.purchaseId()))
                .isPresent();
        if (!alreadySold) {
            throw BusinessException.of(VehiclesError.VEHICLE_COMMAND_CONTRADICTORY);
        }
        return VehicleCommandOutcome.NO_EFFECT;
    }

    private record Sale(UUID messageId, UUID purchaseId, long soldPriceCents, Instant soldAt) {
    }
}
