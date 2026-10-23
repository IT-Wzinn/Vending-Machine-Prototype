package com.vendingmachine.order.entity;

import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.product.entity.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "public_order_code", nullable = false, unique = true, length = 50)
    private String publicOrderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price_vnd", nullable = false)
    private Integer unitPriceVnd;

    @Column(name = "total_amount_vnd", nullable = false)
    private Integer totalAmountVnd;

    @Column(name = "transfer_reference", nullable = false, unique = true, length = 50)
    private String transferReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "payment_expires_at", nullable = false)
    private Instant paymentExpiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "dispensing_started_at")
    private Instant dispensingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "is_reservation_held", nullable = false)
    private boolean reservationHeld = true;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Order() {
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPublicOrderCode() { return publicOrderCode; }
    public void setPublicOrderCode(String publicOrderCode) { this.publicOrderCode = publicOrderCode; }

    public Machine getMachine() { return machine; }
    public void setMachine(Machine machine) { this.machine = machine; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getUnitPriceVnd() { return unitPriceVnd; }
    public void setUnitPriceVnd(Integer unitPriceVnd) { this.unitPriceVnd = unitPriceVnd; }

    public Integer getTotalAmountVnd() { return totalAmountVnd; }
    public void setTotalAmountVnd(Integer totalAmountVnd) { this.totalAmountVnd = totalAmountVnd; }

    public String getTransferReference() { return transferReference; }
    public void setTransferReference(String transferReference) { this.transferReference = transferReference; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Instant getPaymentExpiresAt() { return paymentExpiresAt; }
    public void setPaymentExpiresAt(Instant paymentExpiresAt) { this.paymentExpiresAt = paymentExpiresAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getDispensingStartedAt() { return dispensingStartedAt; }
    public void setDispensingStartedAt(Instant dispensingStartedAt) { this.dispensingStartedAt = dispensingStartedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public boolean isReservationHeld() { return reservationHeld; }
    public void setReservationHeld(boolean reservationHeld) { this.reservationHeld = reservationHeld; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
