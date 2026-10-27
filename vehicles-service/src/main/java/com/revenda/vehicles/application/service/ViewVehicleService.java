package com.revenda.vehicles.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.ports.in.ViewVehicleUseCase;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViewVehicleService implements ViewVehicleUseCase {

    private final LoadVehiclePort stock;

    @Override
    @Transactional(readOnly = true)
    public Vehicle view(UUID id, boolean staff) {
        return stock.findById(id)
                .filter(vehicle -> staff || vehicle.isForSale())
                .orElseThrow(BusinessException::notFound);
    }
}
