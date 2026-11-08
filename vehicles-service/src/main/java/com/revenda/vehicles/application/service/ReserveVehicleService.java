package com.revenda.vehicles.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.common.jdbc.Transactions;
import com.revenda.common.money.Money;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.in.ReserveVehicleCommand;
import com.revenda.vehicles.domain.ports.in.ReserveVehicleUseCase;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada pedido de reserva soma um em {@code vehicles.reservations}, com o resultado: {@code reserved}, {@code repeated}
 * (a mesma compra pediu de novo) ou {@code rejected} (o veículo é de outra compra, foi vendido ou não existe).
 */
@Service
@RequiredArgsConstructor
public class ReserveVehicleService implements ReserveVehicleUseCase {

    static final String RESERVATIONS_METRIC = "vehicles.reservations";

    private final SaveVehiclePort vehicles;
    private final LoadVehiclePort stock;
    private final AuditRecorder audit;
    private final MeterRegistry meters;
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
            Transactions.afterCommit(() -> count("reserved"));
            return reserved.get();
        }
        Optional<Money> repeated = stock.findById(command.vehicleId())
                .filter(vehicle -> vehicle.isReservedFor(command.purchaseId()))
                .map(vehicle -> vehicle.details().price());
        if (repeated.isEmpty()) {
            count("rejected");
            throw BusinessException.of(VehiclesError.VEHICLE_UNAVAILABLE);
        }
        Transactions.afterCommit(() -> count("repeated"));
        return repeated.get();
    }

    private void count(String outcome) {
        meters.counter(RESERVATIONS_METRIC, "outcome", outcome).increment();
    }

    private record Reservation(UUID purchaseId, long priceCents) {
    }
}
