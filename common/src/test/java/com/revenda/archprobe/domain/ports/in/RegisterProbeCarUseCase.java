package com.revenda.archprobe.domain.ports.in;

import com.revenda.archprobe.domain.model.ProbeCar;
import com.revenda.common.money.Money;

public interface RegisterProbeCarUseCase {

    ProbeCar register(Money price);
}
