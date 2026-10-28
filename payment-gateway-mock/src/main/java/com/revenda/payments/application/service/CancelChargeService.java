package com.revenda.payments.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.payments.domain.model.Charge;
import com.revenda.payments.domain.model.ChargeChange;
import com.revenda.payments.domain.ports.in.CancelChargeUseCase;
import com.revenda.payments.domain.ports.out.SaveChargePort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelChargeService implements CancelChargeUseCase {

    private final SaveChargePort charges;
    private final Clock clock;

    @Override
    public Charge cancel(String chargeId) {
        ChargeChange change = charges.change(chargeId, charge -> charge.cancel(clock.instant()))
                .orElseThrow(BusinessException::notFound);
        if (change.moved()) {
            log.info("Cobrança {} cancelada", chargeId);
        }
        return change.after();
    }
}
