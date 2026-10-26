package com.revenda.archprobe.infrastructure.adapters.in.web;

import com.revenda.archprobe.domain.ports.in.RegisterProbeCarUseCase;
import org.springframework.beans.factory.annotation.Autowired;

public class ProbeCarControllerWithFieldInjection {

    @Autowired
    private RegisterProbeCarUseCase registerCar;
}
