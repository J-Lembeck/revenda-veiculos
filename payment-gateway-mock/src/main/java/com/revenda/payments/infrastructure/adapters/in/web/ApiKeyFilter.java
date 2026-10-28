package com.revenda.payments.infrastructure.adapters.in.web;

import com.revenda.common.error.CommonError;
import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toda rota pede a chave de API no {@code X-Api-Key}, menos o {@code GET /actuator/health}. Sem ela ou errada,
 * {@code 401 UNAUTHENTICATED} no formato de erro comum. A comparação é em tempo constante.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";
    static final String MESSAGE = "Envie a chave de API do gateway no cabeçalho X-Api-Key.";
    private static final String HEALTH = "/actuator/health";

    private final byte[] apiKey;
    private final ApiErrorWriter errors;

    public ApiKeyFilter(String apiKey, ApiErrorWriter errors) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equals(request.getMethod()) && (path.equals(HEALTH) || path.startsWith(HEALTH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || !MessageDigest.isEqual(apiKey, presented.getBytes(StandardCharsets.UTF_8))) {
            errors.write(request, response, CommonError.UNAUTHENTICATED, MESSAGE, List.of());
            return;
        }
        chain.doFilter(request, response);
    }
}
