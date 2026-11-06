package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.VehicleReservation;
import java.util.UUID;

/** Pede ao estoque o veículo para a compra (passo 2 da saga), sem repetir: a falha vira compensação. */
public interface ReserveVehiclePort {

    VehicleReservation reserve(UUID vehicleId, UUID purchaseId);
}
