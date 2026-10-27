package com.revenda.vehicles.domain.ports.in;

import com.revenda.vehicles.domain.model.Vehicle;
import java.util.UUID;

public interface ViewVehicleUseCase {

    /** O veículo pelo id; quem não é da equipe só vê o que está à venda, e o resto é {@code 404}. */
    Vehicle view(UUID id, boolean staff);
}
