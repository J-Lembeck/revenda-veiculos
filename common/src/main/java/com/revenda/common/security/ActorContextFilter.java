package com.revenda.common.security;

import com.revenda.common.observability.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActorContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ActorContextHolder.set(actorOf(request));
        try {
            chain.doFilter(request, response);
        } finally {
            ActorContextHolder.clear();
        }
    }

    private static ActorContext actorOf(HttpServletRequest request) {
        String requestId = RequestIdFilter.of(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof ActorAuthentication actor
                ? new ActorContext(actor.getActorType(), actor.getName(), actor.getRole(), requestId)
                : ActorContext.anonymous(requestId);
    }
}
