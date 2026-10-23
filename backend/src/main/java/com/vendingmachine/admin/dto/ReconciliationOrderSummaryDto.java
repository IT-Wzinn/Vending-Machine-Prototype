package com.vendingmachine.admin.dto;

import com.vendingmachine.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public class ReconciliationOrderSummaryDto {

    private UUID orderId;
    private String publicOrderCode;
    private OrderStatus status;
    private String machineCode;
    private String productName;
    private Integer slotNumber;
    private Integer quantity;
    private Integer totalAmountVnd;
    private String transferReference;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean hasDispenseCommand;
    private String latestDispenseStatus;
    private boolean paymentVerified;
    private Boolean isReservationHeld;

    public ReconciliationOrderSummaryDto() {
    }

    public Boolean getIsReservationHeld() { return isReservationHeld; }
    public void setIsReservationHeld(Boolean isReservationHeld) { this.isReservationHeld = isReservationHeld; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getPublicOrderCode() { return publicOrderCode; }
    public void setPublicOrderCode(String publicOrderCode) { this.publicOrderCode = publicOrderCode; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getSlotNumber() { return slotNumber; }
    public void setSlotNumber(Integer slotNumber) { this.slotNumber = slotNumber; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getTotalAmountVnd() { return totalAmountVnd; }
    public void setTotalAmountVnd(Integer totalAmountVnd) { this.totalAmountVnd = totalAmountVnd; }

    public String getTransferReference() { return transferReference; }
    public void setTransferReference(String transferReference) { this.transferReference = transferReference; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isHasDispenseCommand() { return hasDispenseCommand; }
    public void setHasDispenseCommand(boolean hasDispenseCommand) { this.hasDispenseCommand = hasDispenseCommand; }

    public String getLatestDispenseStatus() { return latestDispenseStatus; }
    public void setLatestDispenseStatus(String latestDispenseStatus) { this.latestDispenseStatus = latestDispenseStatus; }

    public boolean isPaymentVerified() { return paymentVerified; }
    public void setPaymentVerified(boolean paymentVerified) { this.paymentVerified = paymentVerified; }
}
