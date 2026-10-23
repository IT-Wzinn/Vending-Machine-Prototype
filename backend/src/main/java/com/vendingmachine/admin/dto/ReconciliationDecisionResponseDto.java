package com.vendingmachine.admin.dto;

import com.vendingmachine.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public class ReconciliationDecisionResponseDto {

    private UUID auditId;
    private UUID orderId;
    private String publicOrderCode;
    private String action;
    private OrderStatus previousStatus;
    private OrderStatus currentStatus;
    private String reason;
    private String confirmationReference;
    private boolean reservationReleased;
    private Instant processedAt;
    private String message;

    public ReconciliationDecisionResponseDto() {
    }

    public ReconciliationDecisionResponseDto(UUID auditId, UUID orderId, String publicOrderCode,
                                             String action, OrderStatus previousStatus, OrderStatus currentStatus,
                                             String reason, String confirmationReference,
                                             boolean reservationReleased, Instant processedAt, String message) {
        this.auditId = auditId;
        this.orderId = orderId;
        this.publicOrderCode = publicOrderCode;
        this.action = action;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.reason = reason;
        this.confirmationReference = confirmationReference;
        this.reservationReleased = reservationReleased;
        this.processedAt = processedAt;
        this.message = message;
    }

    public UUID getAuditId() { return auditId; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getPublicOrderCode() { return publicOrderCode; }
    public void setPublicOrderCode(String publicOrderCode) { this.publicOrderCode = publicOrderCode; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public OrderStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(OrderStatus previousStatus) { this.previousStatus = previousStatus; }

    public OrderStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(OrderStatus currentStatus) { this.currentStatus = currentStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getConfirmationReference() { return confirmationReference; }
    public void setConfirmationReference(String confirmationReference) { this.confirmationReference = confirmationReference; }

    public boolean isReservationReleased() { return reservationReleased; }
    public void setReservationReleased(boolean reservationReleased) { this.reservationReleased = reservationReleased; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
