package com.revenda.vehicles.domain.ports.in;

import com.revenda.common.paging.Page;
import com.revenda.common.paging.Paging;
import com.revenda.vehicles.domain.model.Vehicle;

public interface ListSoldVehiclesUseCase {

    Page<Vehicle> list(Paging paging);
}
