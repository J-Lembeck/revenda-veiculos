package com.revenda.payments.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.payments.domain.model.Charge;
import com.revenda.payments.domain.model.ChargeChange;
import com.revenda.payments.domain.model.PaymentEvent;
import com.revenda.payments.domain.ports.in.RefundChargeUseCase;
import com.revenda.payments.domain.ports.out.PublishPaymentEventPort;
import com.revenda.payments.domain.ports.out.SaveChargePort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Só o estorno que muda a cobrança avisa o webhook; o repetido não manda outro {@code CHARGE_REFUNDED}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundChargeService implements RefundChargeUseCase {

    private final SaveChargePort charges;
    private final PublishPaymentEventPort events;
    private final Clock clock;

    @Override
    public Charge refund(String chargeId) {
        ChargeChange change = charges.change(chargeId, charge -> charge.refund(clock.instant()))
                .orElseThrow(BusinessException::notFound);
        if (change.moved()) {
            PaymentEvent refunded = PaymentEvent.refunded(GatewayIds.event(), change.after());
            log.info("Cobrança {} estornada; evento {}", chargeId, refunded.id());
            events.publish(refunded, 1);
        }
        return change.after();
    }
}
