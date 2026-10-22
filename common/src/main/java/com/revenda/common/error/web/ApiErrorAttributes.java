package com.revenda.common.error.web;

import com.revenda.common.error.CommonError;
import com.revenda.common.observability.RequestIdFilter;
import jakarta.servlet.RequestDispatcher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

public class ApiErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
        CommonError error = ErrorStatus.errorOf(HttpStatusCode.valueOf(status instanceof Integer code ? code : 500));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.name());
        body.put("message", error.getMessage());
        body.put("requestId", RequestIdFilter.of(request));
        body.put("fields", List.of());
        return body;
    }
}
