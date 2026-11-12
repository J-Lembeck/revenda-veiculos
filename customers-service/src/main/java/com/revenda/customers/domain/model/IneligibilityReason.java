package com.revenda.customers.domain.model;

/** Por que a conta não pode comprar, na ordem em que se confere. */
public enum IneligibilityReason {
    PROFILE_MISSING,
    UNDERAGE
}
