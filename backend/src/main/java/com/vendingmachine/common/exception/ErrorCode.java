package com.vendingmachine.common.exception;

public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String MACHINE_NOT_FOUND = "MACHINE_NOT_FOUND";
    public static final String MACHINE_INACTIVE = "MACHINE_INACTIVE";
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String PRODUCT_NOT_IN_MACHINE = "PRODUCT_NOT_IN_MACHINE";
    public static final String PRODUCT_OUT_OF_STOCK = "PRODUCT_OUT_OF_STOCK";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_NOT_PAID = "ORDER_NOT_PAID";
    public static final String DISPENSE_COMMAND_NOT_FOUND = "DISPENSE_COMMAND_NOT_FOUND";
    public static final String DEVICE_OFFLINE = "DEVICE_OFFLINE";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String CSRF_HEADER_MISSING = "CSRF_HEADER_MISSING";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
}

