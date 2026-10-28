package com.revenda.payments.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.payments.domain.model.Charge;
import com.revenda.payments.domain.model.GatewayError;
import com.revenda.payments.domain.ports.in.ChargeCreation;
import com.revenda.payments.domain.ports.in.CreateChargeCommand;
import com.revenda.payments.domain.ports.in.CreateChargeUseCase;
import com.revenda.payments.domain.ports.out.SaveChargePort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateChargeService implements CreateChargeUseCase {

    private final SaveChargePort charges;
    private final Clock clock;

    @Override
    public ChargeCreation create(CreateChargeCommand command) {
        Charge requested = Charge.open(GatewayIds.charge(), command.referenceId(), command.amount(), command.dueAt(),
                command.payer(), clock.instant());
        Charge stored = charges.addIfAbsent(requested);
        if (stored == requested) {
            log.info("Cobrança {} criada para a referência {}", stored.id(), stored.referenceId());
            return new ChargeCreation(stored, true);
        }
        if (!stored.repeats(requested)) {
            throw BusinessException.of(GatewayError.CHARGE_REFERENCE_CONFLICT);
        }
        return new ChargeCreation(stored, false);
    }
}
