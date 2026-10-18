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
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
}
