package com.revenda.vehicles.domain.ports.in;

import com.revenda.vehicles.domain.model.Page;
import com.revenda.vehicles.domain.model.Paging;
import com.revenda.vehicles.domain.model.Vehicle;

public interface ListSoldVehiclesUseCase {

    Page<Vehicle> list(Paging paging);
}
