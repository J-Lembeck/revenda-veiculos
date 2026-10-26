package com.revenda.archprobe.infrastructure.adapters.in.web;

import com.revenda.archprobe.domain.ports.in.RegisterProbeCarUseCase;
import com.revenda.common.money.Money;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProbeCarController {

    private final RegisterProbeCarUseCase registerCar;

    public long register(long priceCents) {
        return registerCar.register(Money.ofCents(priceCents)).price().toCents();
    }
}
