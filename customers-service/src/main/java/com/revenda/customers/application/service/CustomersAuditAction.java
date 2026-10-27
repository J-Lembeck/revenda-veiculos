package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditAction;

public enum CustomersAuditAction implements AuditAction {
    CUSTOMER_REGISTERED,
    CUSTOMER_UPDATED,
    CUSTOMER_CPF_REVEALED
}
