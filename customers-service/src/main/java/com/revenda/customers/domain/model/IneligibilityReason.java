package com.revenda.customers.domain.model;

/** Por que a conta não pode comprar (RN-05), na ordem em que se confere. */
public enum IneligibilityReason {
    PROFILE_MISSING,
    UNDERAGE
}
