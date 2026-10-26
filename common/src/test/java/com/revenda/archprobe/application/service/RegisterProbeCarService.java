package com.revenda.archprobe.application.service;

import com.revenda.archprobe.domain.model.ProbeCar;
import com.revenda.archprobe.domain.ports.in.RegisterProbeCarUseCase;
import com.revenda.archprobe.domain.ports.out.SaveProbeCarPort;
import com.revenda.common.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RegisterProbeCarService implements RegisterProbeCarUseCase {

    private final SaveProbeCarPort cars;
    private final Clock clock;

    @Override
    public ProbeCar register(Money price) {
        ProbeCar car = new ProbeCar(UUID.randomUUID(), price, Instant.now(clock));
        cars.save(car);
        return car;
    }
}
