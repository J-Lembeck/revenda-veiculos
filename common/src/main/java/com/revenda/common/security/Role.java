package com.revenda.common.security;

import java.util.Arrays;
import java.util.Optional;

public enum Role {
    CUSTOMER,
    SELLER,
    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    static Optional<Role> of(String value) {
        return Arrays.stream(values()).filter(role -> role.name().equals(value)).findFirst();
    }
}
