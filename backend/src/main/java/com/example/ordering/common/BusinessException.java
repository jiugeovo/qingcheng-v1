package com.example.ordering.common;

import org.springframework.http.HttpStatus;

public final class BusinessException extends RuntimeException {
    private final BusinessErrorCode code;
    private final HttpStatus status;

    public BusinessException(BusinessErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public BusinessErrorCode code() { return code; }

    public HttpStatus status() { return status; }
}
