package com.example.ordering.security;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.common.BusinessErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException, ServletException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, BusinessErrorCode.UNAUTHORIZED, "请先登录");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, BusinessErrorCode.FORBIDDEN, "没有权限访问该资源");
    }

    private void writeError(HttpServletResponse response, int status, BusinessErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Error-Code", code.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(message));
    }
}
