package com.vendingmachine.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
