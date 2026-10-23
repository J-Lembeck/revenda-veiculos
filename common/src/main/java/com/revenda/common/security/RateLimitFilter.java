package com.revenda.common.security;

import com.revenda.common.error.CommonError;
import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Roda antes da validação do token, para a chamada sem token ou com token inválido também contar. */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final List<LimitedRoute> routes;
    private final ApiErrorWriter errorWriter;

    public RateLimitFilter(RateLimiter rateLimiter, List<RateLimitRule> rules, PathPatternRequestMatcher.Builder matchers,
            ApiErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.routes = rules.stream()
                .map(rule -> new LimitedRoute(matchers.matcher(rule.method(), rule.pattern()), rule))
                .toList();
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (LimitedRoute route : routes) {
            if (route.matcher().matches(request)) {
                Optional<Duration> wait = rateLimiter.tryConsume(route.rule(), ClientIp.networkOf(request));
                if (wait.isPresent()) {
                    refuse(request, response, wait.get());
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, Duration wait) throws IOException {
        long seconds = Math.max(1, wait.plusSeconds(1).minusNanos(1).toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        errorWriter.write(request, response, CommonError.RATE_LIMITED);
    }

    private record LimitedRoute(RequestMatcher matcher, RateLimitRule rule) {
    }
}
