package com.revenda.common.error;

import java.util.List;
import java.util.Objects;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final transient ErrorCode error;
    private final List<FieldViolation> fields;

    protected BusinessException(ErrorCode error, String message, List<FieldViolation> fields) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
        this.fields = List.copyOf(fields);
    }

    public static BusinessException of(ErrorCode error) {
        return new BusinessException(error, error.getMessage(), List.of());
    }

    public static BusinessException of(ErrorCode error, String message) {
        return new BusinessException(error, message, List.of());
    }

    public static BusinessException invalid(FieldViolation... fields) {
        return new BusinessException(CommonError.VALIDATION_FAILED, CommonError.VALIDATION_FAILED.getMessage(),
                List.of(fields));
    }

    public static BusinessException invalidField(String field, String code, String message) {
        return invalid(new FieldViolation(field, code, message));
    }

    public static BusinessException notFound() {
        return of(CommonError.NOT_FOUND);
    }

    public String getCode() {
        return error.name();
    }

    public ErrorKind getKind() {
        return error.getKind();
    }
}
