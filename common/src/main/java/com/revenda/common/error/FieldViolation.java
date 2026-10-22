package com.revenda.common.error;

import java.util.Objects;

public record FieldViolation(String field, String code, String message) {

    public FieldViolation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
