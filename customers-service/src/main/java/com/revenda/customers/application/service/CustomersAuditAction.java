package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditAction;

public enum CustomersAuditAction implements AuditAction {
    CUSTOMER_REGISTERED,
    CUSTOMER_UPDATED,
    CUSTOMER_CPF_REVEALED,
    CUSTOMER_PAYER_DATA_READ,
    CUSTOMER_TRANSFER_DATA_READ,
    CUSTOMER_CPF_CHECKED
}
