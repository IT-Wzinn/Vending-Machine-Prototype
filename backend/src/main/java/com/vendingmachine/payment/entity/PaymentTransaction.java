package com.vendingmachine.payment.entity;

import com.vendingmachine.order.entity.Order;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    private UUID id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_transaction_id", nullable = false, length = 100)
    private String providerTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "amount_vnd", nullable = false)
    private Integer amountVnd;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "VND";

    @Column(name = "direction", nullable = false, length = 20)
    private String direction = "INCOMING";

    @Column(name = "transaction_status", nullable = false, length = 50)
    private String transactionStatus;

    @Column(name = "transfer_reference", length = 100)
    private String transferReference;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "verification_status", nullable = false, length = 50)
    private String verificationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PaymentTransaction() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public Integer getAmountVnd() { return amountVnd; }
    public void setAmountVnd(Integer amountVnd) { this.amountVnd = amountVnd; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(String transactionStatus) { this.transactionStatus = transactionStatus; }

    public String getTransferReference() { return transferReference; }
    public void setTransferReference(String transferReference) { this.transferReference = transferReference; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
