package com.vendingmachine.order.dto;

import java.time.Instant;
import java.util.UUID;

public class OrderStatusResponseDto {

    private UUID orderId;
    private String status;
    private Instant updatedAt;
    private String message;

    public OrderStatusResponseDto() {
    }

    public OrderStatusResponseDto(UUID orderId, String status, Instant updatedAt, String message) {
        this.orderId = orderId;
        this.status = status;
        this.updatedAt = updatedAt;
        this.message = message;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
