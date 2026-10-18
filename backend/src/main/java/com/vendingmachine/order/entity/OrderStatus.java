package com.vendingmachine.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    DISPENSING,
    COMPLETED,
    DISPENSE_FAILED,
    NEEDS_RECONCILIATION,
    EXPIRED,
    CANCELLED
}
