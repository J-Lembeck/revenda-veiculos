package com.revenda.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    public static String of(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof String requestId ? requestId : null;
    }

    public static String of(RequestAttributes request) {
        return request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof String requestId
                ? requestId
                : null;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request);
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(HttpServletRequest request) {
        String current = of(request);
        if (current != null) {
            return current;
        }
        String incoming = request.getHeader(HEADER);
        return incoming != null && ACCEPTED.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }
}
