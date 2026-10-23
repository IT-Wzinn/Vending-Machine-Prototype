package com.vendingmachine.admin.dto;

import com.vendingmachine.order.entity.OrderStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReconciliationOrderDetailDto {

    private UUID orderId;
    private String publicOrderCode;
    private OrderStatus status;
    private Integer totalAmountVnd;
    private Integer quantity;
    private String transferReference;
    private Instant paymentExpiresAt;
    private Instant paidAt;
    private Instant dispensingStartedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Boolean isReservationHeld;

    // Machine info
    private UUID machineId;
    private String machineCode;
    private String machineName;

    // Product info
    private UUID productId;
    private String productName;
    private Integer slotNumber;
    private Integer currentStockQuantity;
    private Integer currentReservedQuantity;
    private Integer unitPriceVnd;

    // Sub-records
    private List<ReconciliationPaymentTxDto> paymentTransactions = new ArrayList<>();
    private List<ReconciliationDispenseCmdDto> dispenseCommands = new ArrayList<>();
    private List<ReconciliationDeviceEventDto> deviceEvents = new ArrayList<>();
    private List<ReconciliationAuditRecordDto> auditLogs = new ArrayList<>();

    public ReconciliationOrderDetailDto() {
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getPublicOrderCode() { return publicOrderCode; }
    public void setPublicOrderCode(String publicOrderCode) { this.publicOrderCode = publicOrderCode; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Integer getTotalAmountVnd() { return totalAmountVnd; }
    public void setTotalAmountVnd(Integer totalAmountVnd) { this.totalAmountVnd = totalAmountVnd; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getTransferReference() { return transferReference; }
    public void setTransferReference(String transferReference) { this.transferReference = transferReference; }

    public Instant getPaymentExpiresAt() { return paymentExpiresAt; }
    public void setPaymentExpiresAt(Instant paymentExpiresAt) { this.paymentExpiresAt = paymentExpiresAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getDispensingStartedAt() { return dispensingStartedAt; }
    public void setDispensingStartedAt(Instant dispensingStartedAt) { this.dispensingStartedAt = dispensingStartedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getIsReservationHeld() { return isReservationHeld; }
    public void setIsReservationHeld(Boolean isReservationHeld) { this.isReservationHeld = isReservationHeld; }

    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public String getMachineName() { return machineName; }
    public void setMachineName(String machineName) { this.machineName = machineName; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getSlotNumber() { return slotNumber; }
    public void setSlotNumber(Integer slotNumber) { this.slotNumber = slotNumber; }

    public Integer getCurrentStockQuantity() { return currentStockQuantity; }
    public void setCurrentStockQuantity(Integer currentStockQuantity) { this.currentStockQuantity = currentStockQuantity; }

    public Integer getCurrentReservedQuantity() { return currentReservedQuantity; }
    public void setCurrentReservedQuantity(Integer currentReservedQuantity) { this.currentReservedQuantity = currentReservedQuantity; }

    public Integer getUnitPriceVnd() { return unitPriceVnd; }
    public void setUnitPriceVnd(Integer unitPriceVnd) { this.unitPriceVnd = unitPriceVnd; }

    public List<ReconciliationPaymentTxDto> getPaymentTransactions() { return paymentTransactions; }
    public void setPaymentTransactions(List<ReconciliationPaymentTxDto> paymentTransactions) { this.paymentTransactions = paymentTransactions; }

    public List<ReconciliationDispenseCmdDto> getDispenseCommands() { return dispenseCommands; }
    public void setDispenseCommands(List<ReconciliationDispenseCmdDto> dispenseCommands) { this.dispenseCommands = dispenseCommands; }

    public List<ReconciliationDeviceEventDto> getDeviceEvents() { return deviceEvents; }
    public void setDeviceEvents(List<ReconciliationDeviceEventDto> deviceEvents) { this.deviceEvents = deviceEvents; }

    public List<ReconciliationAuditRecordDto> getAuditLogs() { return auditLogs; }
    public void setAuditLogs(List<ReconciliationAuditRecordDto> auditLogs) { this.auditLogs = auditLogs; }

    // Nested sub-record DTOs
    public static class ReconciliationPaymentTxDto {
        private UUID transactionId;
        private String providerTransactionId;
        private Integer amountVnd;
        private String transferReference;
        private String verificationStatus;
        private String transactionStatus;
        private Instant receivedAt;
        private Instant createdAt;

        public ReconciliationPaymentTxDto() {}

        public ReconciliationPaymentTxDto(UUID transactionId, String providerTransactionId, Integer amountVnd,
                                          String transferReference, String verificationStatus, String transactionStatus,
                                          Instant receivedAt, Instant createdAt) {
            this.transactionId = transactionId;
            this.providerTransactionId = providerTransactionId;
            this.amountVnd = amountVnd;
            this.transferReference = transferReference;
            this.verificationStatus = verificationStatus;
            this.transactionStatus = transactionStatus;
            this.receivedAt = receivedAt;
            this.createdAt = createdAt;
        }

        public UUID getTransactionId() { return transactionId; }
        public String getProviderTransactionId() { return providerTransactionId; }
        public Integer getAmountVnd() { return amountVnd; }
        public String getTransferReference() { return transferReference; }
        public String getVerificationStatus() { return verificationStatus; }
        public String getTransactionStatus() { return transactionStatus; }
        public Instant getReceivedAt() { return receivedAt; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public static class ReconciliationDispenseCmdDto {
        private UUID commandId;
        private Integer slotNumber;
        private String status;
        private Integer attemptCount;
        private String failureCode;
        private String failureMessage;
        private Instant publishedAt;
        private Instant acknowledgedAt;
        private Instant finishedAt;
        private Instant createdAt;

        public ReconciliationDispenseCmdDto() {}

        public ReconciliationDispenseCmdDto(UUID commandId, Integer slotNumber, String status, Integer attemptCount,
                                            String failureCode, String failureMessage, Instant publishedAt,
                                            Instant acknowledgedAt, Instant finishedAt, Instant createdAt) {
            this.commandId = commandId;
            this.slotNumber = slotNumber;
            this.status = status;
            this.attemptCount = attemptCount;
            this.failureCode = failureCode;
            this.failureMessage = failureMessage;
            this.publishedAt = publishedAt;
            this.acknowledgedAt = acknowledgedAt;
            this.finishedAt = finishedAt;
            this.createdAt = createdAt;
        }

        public UUID getCommandId() { return commandId; }
        public Integer getSlotNumber() { return slotNumber; }
        public String getStatus() { return status; }
        public Integer getAttemptCount() { return attemptCount; }
        public String getFailureCode() { return failureCode; }
        public String getFailureMessage() { return failureMessage; }
        public Instant getPublishedAt() { return publishedAt; }
        public Instant getAcknowledgedAt() { return acknowledgedAt; }
        public Instant getFinishedAt() { return finishedAt; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public static class ReconciliationDeviceEventDto {
        private UUID eventId;
        private UUID commandId;
        private String eventType;
        private Integer slotNumber;
        private Instant createdAt;

        public ReconciliationDeviceEventDto() {}

        public ReconciliationDeviceEventDto(UUID eventId, UUID commandId, String eventType, Integer slotNumber, Instant createdAt) {
            this.eventId = eventId;
            this.commandId = commandId;
            this.eventType = eventType;
            this.slotNumber = slotNumber;
            this.createdAt = createdAt;
        }

        public UUID getEventId() { return eventId; }
        public UUID getCommandId() { return commandId; }
        public String getEventType() { return eventType; }
        public Integer getSlotNumber() { return slotNumber; }
        public Instant getCreatedAt() { return createdAt; }
    }

    public static class ReconciliationAuditRecordDto {
        private UUID auditId;
        private String adminUsername;
        private String action;
        private OrderStatus previousStatus;
        private OrderStatus newStatus;
        private String reason;
        private String confirmationReference;
        private Instant createdAt;

        public ReconciliationAuditRecordDto() {}

        public ReconciliationAuditRecordDto(UUID auditId, String adminUsername, String action,
                                            OrderStatus previousStatus, OrderStatus newStatus,
                                            String reason, String confirmationReference, Instant createdAt) {
            this.auditId = auditId;
            this.adminUsername = adminUsername;
            this.action = action;
            this.previousStatus = previousStatus;
            this.newStatus = newStatus;
            this.reason = reason;
            this.confirmationReference = confirmationReference;
            this.createdAt = createdAt;
        }

        public UUID getAuditId() { return auditId; }
        public String getAdminUsername() { return adminUsername; }
        public String getAction() { return action; }
        public OrderStatus getPreviousStatus() { return previousStatus; }
        public OrderStatus getNewStatus() { return newStatus; }
        public String getReason() { return reason; }
        public String getConfirmationReference() { return confirmationReference; }
        public Instant getCreatedAt() { return createdAt; }
    }
}
