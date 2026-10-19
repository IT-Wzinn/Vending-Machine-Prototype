package com.vendingmachine.dispense.entity;

public enum DispenseCommandStatus {
    PENDING,
    PUBLISHED,
    ACKNOWLEDGED,
    COMPLETED,
    FAILED,
    REJECTED,
    UNCERTAIN,
    TIMEOUT
}
