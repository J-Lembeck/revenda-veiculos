package com.revenda.vehicles.application.service;

import com.revenda.common.paging.Page;
import com.revenda.common.paging.Paging;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.ports.in.ListSoldVehiclesUseCase;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListSoldVehiclesService implements ListSoldVehiclesUseCase {

    private final LoadVehiclePort stock;

    @Override
    @Transactional(readOnly = true)
    public Page<Vehicle> list(Paging paging) {
        return stock.findSold(paging);
    }
}
