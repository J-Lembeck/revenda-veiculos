package com.revenda.vehicles.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.in.EditVehicleCommand;
import com.revenda.vehicles.domain.ports.in.EditVehicleUseCase;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EditVehicleService implements EditVehicleUseCase {

    private final LoadVehiclePort stock;
    private final SaveVehiclePort vehicles;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public Vehicle edit(EditVehicleCommand command) {
        Vehicle current = stock.findById(command.id()).orElseThrow(BusinessException::notFound);
        Vehicle edited = current.edit(command.vehicle(), command.version(), clock.instant());
        if (!vehicles.updateDetails(edited)) {
            throw BusinessException.of(VehiclesError.VERSION_CONFLICT);
        }
        audit.record(AuditEntry.of(VehiclesAuditAction.VEHICLE_UPDATED, Vehicle.ENTITY_TYPE, edited.id(),
                        VehicleSnapshot.summary("Veículo editado", edited))
                .withChange(VehicleSnapshot.of(current), VehicleSnapshot.of(edited)));
        return edited;
    }
}
