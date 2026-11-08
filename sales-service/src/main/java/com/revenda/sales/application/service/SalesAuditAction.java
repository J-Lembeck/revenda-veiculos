package com.revenda.sales.application.service;

import com.revenda.common.audit.AuditAction;

/**
 * As ações do {@code sales} no {@code audit_log}. As mudanças que a própria saga faz (reserva, cobrança, pagamento,
 * estorno, expiração, recuperação, documento emitido) ficam na linha do tempo da compra, com o ator e o motivo de cada
 * uma.
 */
public enum SalesAuditAction implements AuditAction {
    PURCHASE_CREATED,
    PURCHASE_CANCELLED,
    PURCHASE_PICKUP_CONFIRMED,
    PURCHASE_PICKUP_REJECTED,
    PURCHASE_DOCUMENT_DOWNLOADED,
    PAYMENT_WEBHOOK_REJECTED
}
