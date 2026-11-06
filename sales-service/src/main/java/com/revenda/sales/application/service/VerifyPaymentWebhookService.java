package com.revenda.sales.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.common.webhook.WebhookSignature;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.ports.in.VerifyPaymentWebhookUseCase;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class VerifyPaymentWebhookService implements VerifyPaymentWebhookUseCase {

    static final String ENTITY_TYPE = "PAYMENT_WEBHOOK";

    private final WebhookSignature signature;
    private final SalesProperties properties;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;
    private final Clock clock;

    VerifyPaymentWebhookService(SalesProperties properties, AuditRecorder audit, TransactionTemplate transaction,
            Clock clock) {
        this.signature = new WebhookSignature(properties.webhookSecret());
        this.properties = properties;
        this.audit = audit;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public void verify(String header, byte[] body) {
        if (signature.verify(header, body, clock.instant(), properties.webhookTolerance())) {
            return;
        }
        String summary = header == null
                ? "Aviso de pagamento recusado: sem assinatura"
                : "Aviso de pagamento recusado: assinatura que não confere ou carimbo fora da janela";
        transaction.executeWithoutResult(status -> audit.record(
                AuditEntry.of(SalesAuditAction.PAYMENT_WEBHOOK_REJECTED, ENTITY_TYPE, null, summary)));
        log.warn(summary);
        throw BusinessException.of(SalesError.INVALID_SIGNATURE);
    }
}
