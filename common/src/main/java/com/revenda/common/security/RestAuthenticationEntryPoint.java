package com.revenda.common.security;

import com.revenda.common.error.CommonError;
import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/** O de bearer token põe o {@code WWW-Authenticate}; este garante o 401 e o corpo no formato comum. */
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final ApiErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        bearer.commence(request, response, authException);
        errorWriter.write(request, response, CommonError.UNAUTHENTICATED);
    }
}
