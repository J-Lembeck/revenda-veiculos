package com.revenda.archprobe.domain.model;

import java.time.Instant;

public record ProbeCarWithItsOwnClock(Instant registeredAt) {

    public static ProbeCarWithItsOwnClock registeredNow() {
        return new ProbeCarWithItsOwnClock(Instant.now());
    }
}
