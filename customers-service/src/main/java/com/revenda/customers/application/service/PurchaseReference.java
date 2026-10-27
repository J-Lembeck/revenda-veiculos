package com.revenda.customers.application.service;

import java.util.UUID;

/** A compra para a qual o serviço leu o dado completo do cliente, como vai na auditoria. */
record PurchaseReference(UUID purchaseId) {
}
