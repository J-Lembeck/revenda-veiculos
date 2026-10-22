package com.revenda.common.error.web;

import com.revenda.common.error.ErrorCode;
import com.revenda.common.error.FieldViolation;
import java.util.List;

public record ApiError(String code, String message, String requestId, List<FieldViolation> fields) {

    public ApiError {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static ApiError of(ErrorCode error, String requestId) {
        return new ApiError(error.name(), error.getMessage(), requestId, List.of());
    }

    public static ApiError of(ErrorCode error, String message, String requestId, List<FieldViolation> fields) {
        return new ApiError(error.name(), message, requestId, fields);
    }
}
