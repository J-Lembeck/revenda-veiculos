package com.revenda.common.error.web;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.CommonError;
import com.revenda.common.error.FieldViolation;
import com.revenda.common.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String TYPE_INVALID = "TYPE_INVALID";
    static final String TYPE_INVALID_MESSAGE = "Valor em formato inválido.";
    static final String BODY_UNREADABLE_MESSAGE = "O corpo da requisição não é um JSON válido.";

    private static final Map<String, String> FIELD_CODES = Stream.of(
            Map.entry("REQUIRED", List.of("NotNull", "NotBlank", "NotEmpty")),
            Map.entry("SIZE_INVALID", List.of("Size", "Length")),
            Map.entry("OUT_OF_RANGE", List.of("Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero", "Range", "Digits")),
            Map.entry("FORMAT_INVALID", List.of("Pattern", "Email")),
            Map.entry("DATE_INVALID", List.of("Past", "PastOrPresent", "Future", "FutureOrPresent")),
            Map.entry(TYPE_INVALID, List.of("typeMismatch")))
            .flatMap(entry -> entry.getValue().stream().map(constraint -> Map.entry(constraint, entry.getKey())))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.getError(), ex.getMessage(), RequestIdFilter.of(request), ex.getFields());
        return ResponseEntity.status(ErrorStatus.of(ex.getKind())).body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(CommonError.INTERNAL_ERROR, RequestIdFilter.of(request)));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> fields = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream().map(GlobalExceptionHandler::violation),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(error -> violation(error.getObjectName(), error)))
                .toList();
        return validationFailed(CommonError.VALIDATION_FAILED.getMessage(), fields, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> fields = ex.getParameterValidationResults().stream()
                .flatMap(GlobalExceptionHandler::violations)
                .toList();
        return validationFailed(CommonError.VALIDATION_FAILED.getMessage(), fields, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<FieldViolation> fields = ex.getPropertyName() == null
                ? List.of()
                : List.of(new FieldViolation(ex.getPropertyName(), TYPE_INVALID, TYPE_INVALID_MESSAGE));
        return validationFailed(CommonError.VALIDATION_FAILED.getMessage(), fields, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getMostSpecificCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            FieldViolation field = new FieldViolation(path(jackson), TYPE_INVALID, TYPE_INVALID_MESSAGE);
            return validationFailed(CommonError.VALIDATION_FAILED.getMessage(), List.of(field), headers, request);
        }
        return validationFailed(BODY_UNREADABLE_MESSAGE, List.of(), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (request instanceof ServletWebRequest servlet && servlet.getResponse() != null
                && servlet.getResponse().isCommitted()) {
            return null;
        }
        if (statusCode.is5xxServerError()) {
            log.error("Erro do framework com status {}", statusCode.value(), ex);
        }
        ApiError apiError = body instanceof ApiError existing
                ? existing
                : ApiError.of(ErrorStatus.errorOf(statusCode), RequestIdFilter.of(request));
        return ResponseEntity.status(statusCode).headers(headers).body(apiError);
    }

    private ResponseEntity<Object> validationFailed(String message, List<FieldViolation> fields, HttpHeaders headers,
            WebRequest request) {
        ApiError body = ApiError.of(CommonError.VALIDATION_FAILED, message, RequestIdFilter.of(request), fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(body);
    }

    private static Stream<FieldViolation> violations(ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors) {
            return errors.getFieldErrors().stream().map(GlobalExceptionHandler::violation);
        }
        String name = parameterName(result.getMethodParameter());
        return result.getResolvableErrors().stream().map(error -> violation(name, error));
    }

    private static FieldViolation violation(FieldError error) {
        return violation(error.getField(), error);
    }

    private static FieldViolation violation(String field, MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String constraint = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
        String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : TYPE_INVALID_MESSAGE;
        return new FieldViolation(field, FIELD_CODES.getOrDefault(constraint, "INVALID"), message);
    }

    private static String parameterName(MethodParameter parameter) {
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        return Stream.of(
                        param != null ? param.name() : null, param != null ? param.value() : null,
                        path != null ? path.name() : null, path != null ? path.value() : null,
                        header != null ? header.name() : null, header != null ? header.value() : null)
                .filter(name -> name != null && !name.isEmpty())
                .findFirst()
                .orElseGet(parameter::getParameterName);
    }

    private static String path(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
