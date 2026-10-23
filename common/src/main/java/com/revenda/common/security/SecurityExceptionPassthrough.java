package com.revenda.common.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Devolve ao filtro do Spring Security a negação lançada dentro de um controller, que o tratamento genérico de
 * erro transformaria em 500. Fica à frente de todo outro {@code @RestControllerAdvice}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionPassthrough {

    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrow(RuntimeException ex) {
        throw ex;
    }
}
