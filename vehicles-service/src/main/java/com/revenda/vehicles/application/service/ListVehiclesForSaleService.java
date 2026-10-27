package com.revenda.vehicles.application.service;

import com.revenda.vehicles.domain.model.Page;
import com.revenda.vehicles.domain.model.Paging;
import com.revenda.vehicles.domain.model.ShowcaseFilter;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.ports.in.ListVehiclesForSaleUseCase;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListVehiclesForSaleService implements ListVehiclesForSaleUseCase {

    private final LoadVehiclePort stock;

    @Override
    @Transactional(readOnly = true)
    public Page<Vehicle> list(ShowcaseFilter filter, Paging paging) {
        return stock.findForSale(filter, paging);
    }
}
