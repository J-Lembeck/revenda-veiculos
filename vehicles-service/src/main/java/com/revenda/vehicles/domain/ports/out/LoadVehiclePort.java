package com.revenda.vehicles.domain.ports.out;

import com.revenda.vehicles.domain.model.Vehicle;
import java.util.Optional;
import java.util.UUID;

public interface LoadVehiclePort {

    Optional<Vehicle> findById(UUID id);
}
