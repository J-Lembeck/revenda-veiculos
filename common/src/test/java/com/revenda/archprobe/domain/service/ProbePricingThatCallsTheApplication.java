package com.revenda.archprobe.domain.service;

import com.revenda.archprobe.application.service.RegisterProbeCarService;

public record ProbePricingThatCallsTheApplication(RegisterProbeCarService registerCar) {
}
