package com.vendingmachine.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

public class OrderResponseDto {

    private UUID orderId;
    private String publicOrderCode;
    private String machineId;
    private OrderProductDto product;
    private Integer quantity;
    private Integer totalAmountVnd;
    private String status;
    private OrderPaymentDto payment;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private OrderDispensingDto dispensing;
    
    private Instant createdAt;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant updatedAt;

    public OrderResponseDto() {
    }

    public OrderResponseDto(UUID orderId, String publicOrderCode, String machineId,
                            OrderProductDto product, Integer quantity, Integer totalAmountVnd,
                            String status, OrderPaymentDto payment, OrderDispensingDto dispensing,
                            Instant createdAt, Instant updatedAt) {
        this.orderId = orderId;
        this.publicOrderCode = publicOrderCode;
        this.machineId = machineId;
        this.product = product;
        this.quantity = quantity;
        this.totalAmountVnd = totalAmountVnd;
        this.status = status;
        this.payment = payment;
        this.dispensing = dispensing;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getPublicOrderCode() { return publicOrderCode; }
    public void setPublicOrderCode(String publicOrderCode) { this.publicOrderCode = publicOrderCode; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public OrderProductDto getProduct() { return product; }
    public void setProduct(OrderProductDto product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getTotalAmountVnd() { return totalAmountVnd; }
    public void setTotalAmountVnd(Integer totalAmountVnd) { this.totalAmountVnd = totalAmountVnd; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OrderPaymentDto getPayment() { return payment; }
    public void setPayment(OrderPaymentDto payment) { this.payment = payment; }

    public OrderDispensingDto getDispensing() { return dispensing; }
    public void setDispensing(OrderDispensingDto dispensing) { this.dispensing = dispensing; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // Nested Product DTO
    public static class OrderProductDto {
        private UUID productId;
        private String name;
        private Integer priceVnd;

        public OrderProductDto() {}
        public OrderProductDto(UUID productId, String name, Integer priceVnd) {
            this.productId = productId;
            this.name = name;
            this.priceVnd = priceVnd;
        }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getPriceVnd() { return priceVnd; }
        public void setPriceVnd(Integer priceVnd) { this.priceVnd = priceVnd; }
    }

    // Nested Payment DTO
    public static class OrderPaymentDto {
        private String transferReference;
        private String paymentQrData;
        private String bankAccountNumber;
        private String bankAccountName;
        private String bankName;
        private Integer amountVnd;
        private Instant paymentExpiresAt;
        private Instant paidAt;

        public OrderPaymentDto() {}

        public OrderPaymentDto(String transferReference, String paymentQrData,
                               String bankAccountNumber, String bankAccountName, String bankName,
                               Integer amountVnd, Instant paymentExpiresAt, Instant paidAt) {
            this.transferReference = transferReference;
            this.paymentQrData = paymentQrData;
            this.bankAccountNumber = bankAccountNumber;
            this.bankAccountName = bankAccountName;
            this.bankName = bankName;
            this.amountVnd = amountVnd;
            this.paymentExpiresAt = paymentExpiresAt;
            this.paidAt = paidAt;
        }

        public String getTransferReference() { return transferReference; }
        public void setTransferReference(String transferReference) { this.transferReference = transferReference; }

        public String getPaymentQrData() { return paymentQrData; }
        public void setPaymentQrData(String paymentQrData) { this.paymentQrData = paymentQrData; }

        public String getBankAccountNumber() { return bankAccountNumber; }
        public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

        public String getBankAccountName() { return bankAccountName; }
        public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }

        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }

        public Integer getAmountVnd() { return amountVnd; }
        public void setAmountVnd(Integer amountVnd) { this.amountVnd = amountVnd; }

        public Instant getPaymentExpiresAt() { return paymentExpiresAt; }
        public void setPaymentExpiresAt(Instant paymentExpiresAt) { this.paymentExpiresAt = paymentExpiresAt; }

        public Instant getPaidAt() { return paidAt; }
        public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    }

    // Nested Dispensing DTO
    public static class OrderDispensingDto {
        private Instant startedAt;
        private Instant completedAt;
        private String result;
        private String verificationLevel;
        private String message;

        public OrderDispensingDto() {}

        public OrderDispensingDto(Instant startedAt, Instant completedAt, String result,
                                  String verificationLevel, String message) {
            this.startedAt = startedAt;
            this.completedAt = completedAt;
            this.result = result;
            this.verificationLevel = verificationLevel;
            this.message = message;
        }

        public Instant getStartedAt() { return startedAt; }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }

        public String getVerificationLevel() { return verificationLevel; }
        public void setVerificationLevel(String verificationLevel) { this.verificationLevel = verificationLevel; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
