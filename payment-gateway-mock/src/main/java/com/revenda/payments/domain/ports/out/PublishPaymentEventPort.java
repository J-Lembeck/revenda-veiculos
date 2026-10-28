package com.revenda.payments.domain.ports.out;

import com.revenda.payments.domain.model.PaymentEvent;

public interface PublishPaymentEventPort {

    /**
     * Entrega o evento ao webhook {@code deliveries} vezes, em segundo plano, cada uma assinada e reentregue até o
     * destino responder {@code 2xx} ou acabarem as tentativas.
     */
    void publish(PaymentEvent event, int deliveries);
}
