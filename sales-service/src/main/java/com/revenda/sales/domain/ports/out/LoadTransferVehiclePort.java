package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.TransferVehicle;
import java.util.UUID;

/** O veículo para o documento de transferência, lido do estoque na hora. Sem resposta, {@code DependencyUnavailableException}. */
public interface LoadTransferVehiclePort {

    TransferVehicle load(UUID vehicleId);
}
