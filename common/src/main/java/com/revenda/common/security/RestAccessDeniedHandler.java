package com.revenda.common.security;

import com.revenda.common.error.CommonError;
import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    private final ApiErrorWriter errorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        bearer.handle(request, response, accessDeniedException);
        errorWriter.write(request, response, CommonError.FORBIDDEN);
    }
}
