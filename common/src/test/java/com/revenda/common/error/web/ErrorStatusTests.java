package com.revenda.common.error.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.error.CommonError;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;

class ErrorStatusTests {

    @ParameterizedTest
    @CsvSource({
            "VALIDATION_FAILED, 400",
            "REQUEST_NOT_SUPPORTED, 400",
            "UNAUTHENTICATED, 401",
            "FORBIDDEN, 403",
            "NOT_FOUND, 404",
            "IDEMPOTENCY_KEY_REUSED, 422",
            "IDEMPOTENCY_KEY_REQUIRED, 428",
            "RATE_LIMITED, 429",
            "INTERNAL_ERROR, 500",
            "DEPENDENCY_UNAVAILABLE, 503"
    })
    void commonErrorsAnswerTheDocumentedStatus(CommonError error, int status) {
        assertThat(ErrorStatus.of(error.getKind()).value()).isEqualTo(status);
    }

    @ParameterizedTest
    @CsvSource({
            "400, VALIDATION_FAILED",
            "401, UNAUTHENTICATED",
            "403, FORBIDDEN",
            "404, NOT_FOUND",
            "405, REQUEST_NOT_SUPPORTED",
            "406, REQUEST_NOT_SUPPORTED",
            "415, REQUEST_NOT_SUPPORTED",
            "429, RATE_LIMITED",
            "500, INTERNAL_ERROR",
            "503, INTERNAL_ERROR"
    })
    void frameworkStatusesBecomeAStableCode(int status, CommonError error) {
        assertThat(ErrorStatus.errorOf(HttpStatusCode.valueOf(status))).isEqualTo(error);
    }
}
