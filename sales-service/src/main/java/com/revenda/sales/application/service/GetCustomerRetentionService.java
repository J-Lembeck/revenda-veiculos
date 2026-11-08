package com.revenda.sales.application.service;

import com.revenda.sales.domain.ports.in.GetCustomerRetentionUseCase;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCustomerRetentionService implements GetCustomerRetentionUseCase {

    private final LoadPurchasePort purchases;
    private final SalesProperties properties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> retainUntil(UUID customerId) {
        return purchases.historyOf(customerId).retainUntil(properties.retentionAfterSale(), clock.instant());
    }
}
