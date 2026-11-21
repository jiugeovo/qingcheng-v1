package com.example.ordering.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.http.HttpHeaders;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return error(ex.status(), ex.getMessage(), ex.code().name());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), BusinessErrorCode.INVALID_ARGUMENT.name());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不合法");
        return error(HttpStatus.BAD_REQUEST, message, BusinessErrorCode.VALIDATION_FAILED.name());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, "请求参数不合法", BusinessErrorCode.VALIDATION_FAILED.name());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "请求体格式不正确", BusinessErrorCode.VALIDATION_FAILED.name());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持", BusinessErrorCode.VALIDATION_FAILED.name());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "请求的资源不存在", BusinessErrorCode.NOT_FOUND.name());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(ex.getHeaders().getAllow());
        headers.set("X-Error-Code", BusinessErrorCode.INVALID_ARGUMENT.name());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(ApiResponse.fail("请求方法不支持"));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "缺少必要的请求参数", BusinessErrorCode.VALIDATION_FAILED.name());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = "请求失败";
        }
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return error(resolved, message, codeFor(resolved));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "数据已存在", BusinessErrorCode.CONFLICT.name());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "没有权限访问该资源", BusinessErrorCode.FORBIDDEN.name());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled API exception traceId={}", currentTraceId(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误", BusinessErrorCode.INTERNAL_ERROR.name());
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message, String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Error-Code", code);
        return ResponseEntity.status(status).headers(headers).body(ApiResponse.fail(message));
    }

    private static String currentTraceId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object traceId = attributes.getRequest().getAttribute(TraceIdFilter.ATTRIBUTE);
            return traceId == null ? "unavailable" : traceId.toString();
        }
        return "unavailable";
    }

    private static String codeFor(HttpStatus status) {
        if (status == HttpStatus.CONFLICT) return BusinessErrorCode.CONFLICT.name();
        if (status == HttpStatus.FORBIDDEN) return BusinessErrorCode.FORBIDDEN.name();
        if (status == HttpStatus.NOT_FOUND) return BusinessErrorCode.NOT_FOUND.name();
        if (status.is4xxClientError()) return BusinessErrorCode.INVALID_ARGUMENT.name();
        return BusinessErrorCode.INTERNAL_ERROR.name();
    }
}
