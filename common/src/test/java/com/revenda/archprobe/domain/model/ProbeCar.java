package com.revenda.archprobe.domain.model;

import com.revenda.common.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProbeCar(UUID id, Money price, Instant registeredAt, int year, char[] plate, List<String> photos) {

    public ProbeCar(UUID id, Money price, Instant registeredAt) {
        this(id, price, registeredAt, 2024, new char[] {'A', 'B', 'C'}, List.of());
    }
}
