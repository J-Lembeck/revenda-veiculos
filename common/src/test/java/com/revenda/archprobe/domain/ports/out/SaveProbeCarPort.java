package com.revenda.archprobe.domain.ports.out;

import com.revenda.archprobe.domain.model.ProbeCar;

public interface SaveProbeCarPort {

    void save(ProbeCar car);
}
