package com.revenda.payments.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.payments.domain.model.ChargeChange;
import com.revenda.payments.domain.model.PaymentEvent;
import com.revenda.payments.domain.ports.in.SimulatePaymentCommand;
import com.revenda.payments.domain.ports.in.SimulatePaymentUseCase;
import com.revenda.payments.domain.ports.out.PublishPaymentEventPort;
import com.revenda.payments.domain.ports.out.SaveChargePort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatePaymentService implements SimulatePaymentUseCase {

    private final SaveChargePort charges;
    private final PublishPaymentEventPort events;
    private final Clock clock;

    @Override
    public PaymentEvent simulate(SimulatePaymentCommand command) {
        ChargeChange change = charges.change(command.chargeId(),
                        charge -> charge.pay(command.outcome().paidFor(charge.amount()), clock.instant()))
                .orElseThrow(BusinessException::notFound);
        PaymentEvent paid = PaymentEvent.paid(GatewayIds.event(), change.after());
        log.info("Cobrança {} paga na simulação ({}); evento {} em {} entrega(s)", command.chargeId(),
                command.outcome(), paid.id(), command.deliveries());
        events.publish(paid, command.deliveries());
        return paid;
    }
}
