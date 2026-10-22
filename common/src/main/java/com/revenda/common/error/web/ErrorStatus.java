package com.revenda.common.error.web;

import com.revenda.common.error.CommonError;
import com.revenda.common.error.ErrorKind;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public final class ErrorStatus {

    private ErrorStatus() {
    }

    public static HttpStatus of(ErrorKind kind) {
        return switch (kind) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static CommonError errorOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> CommonError.VALIDATION_FAILED;
            case 401 -> CommonError.UNAUTHENTICATED;
            case 403 -> CommonError.FORBIDDEN;
            case 404 -> CommonError.NOT_FOUND;
            case 429 -> CommonError.RATE_LIMITED;
            default -> status.is4xxClientError() ? CommonError.REQUEST_NOT_SUPPORTED : CommonError.INTERNAL_ERROR;
        };
    }
}
