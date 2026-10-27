package com.revenda.vehicles.domain.ports.in;

import com.revenda.vehicles.domain.model.Page;
import com.revenda.vehicles.domain.model.Paging;
import com.revenda.vehicles.domain.model.ShowcaseFilter;
import com.revenda.vehicles.domain.model.Vehicle;

public interface ListVehiclesForSaleUseCase {

    Page<Vehicle> list(ShowcaseFilter filter, Paging paging);
}
