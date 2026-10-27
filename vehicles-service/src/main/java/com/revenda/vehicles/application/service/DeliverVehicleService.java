package com.revenda.vehicles.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.in.DeliverVehicleCommand;
import com.revenda.vehicles.domain.ports.in.DeliverVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.VehicleCommandOutcome;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import com.revenda.vehicles.domain.ports.out.SaveProcessedMessagePort;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliverVehicleService implements DeliverVehicleUseCase {

    private final SaveProcessedMessagePort processed;
    private final SaveVehiclePort vehicles;
    private final LoadVehiclePort stock;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public VehicleCommandOutcome deliver(DeliverVehicleCommand command) {
        Instant now = clock.instant();
        if (!processed.markProcessed(command.messageId(), SaveProcessedMessagePort.VEHICLE_COMMANDS, now)) {
            return VehicleCommandOutcome.DUPLICATE;
        }
        if (vehicles.markDeliveredIfSoldTo(command.vehicleId(), command.purchaseId(), command.deliveredAt(), now)) {
            audit.record(AuditEntry.of(VehiclesAuditAction.VEHICLE_DELIVERED, Vehicle.ENTITY_TYPE, command.vehicleId(),
                            "Veículo entregue na compra " + command.purchaseId())
                    .withExtra(new Delivery(command.messageId(), command.purchaseId(), command.deliveredAt())));
            return VehicleCommandOutcome.APPLIED;
        }
        Optional<Vehicle> vehicle = stock.findById(command.vehicleId());
        if (vehicle.filter(found -> found.isDeliveredTo(command.purchaseId())).isPresent()) {
            return VehicleCommandOutcome.NO_EFFECT;
        }
        if (vehicle.filter(found -> found.isReservedFor(command.purchaseId())).isPresent()) {
            throw BusinessException.of(VehiclesError.VEHICLE_COMMAND_NOT_YET_APPLICABLE);
        }
        throw BusinessException.of(VehiclesError.VEHICLE_COMMAND_CONTRADICTORY);
    }

    private record Delivery(UUID messageId, UUID purchaseId, Instant deliveredAt) {
    }
}
