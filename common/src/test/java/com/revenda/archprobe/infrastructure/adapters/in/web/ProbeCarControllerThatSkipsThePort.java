package com.revenda.archprobe.infrastructure.adapters.in.web;

import com.revenda.archprobe.application.service.RegisterProbeCarService;

public record ProbeCarControllerThatSkipsThePort(RegisterProbeCarService registerCar) {
}
