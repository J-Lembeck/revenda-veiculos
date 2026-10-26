package com.revenda.common.error.web;

import com.revenda.common.error.ErrorCode;
import com.revenda.common.error.FieldViolation;
import com.revenda.common.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode error) throws IOException {
        write(request, response, error, error.getMessage(), List.of());
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode error, String message,
            List<FieldViolation> fields) throws IOException {
        response.setStatus(ErrorStatus.of(error.getKind()).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiError.of(error, message, RequestIdFilter.of(request), fields));
    }
}
