package com.vendingmachine.payment.entity;

public enum VerificationStatus {
    VERIFIED,
    UNMATCHED,
    UNDERPAID,
    LATE_PAYMENT,
    DUPLICATE,
    INVALID_SIGNATURE
}
