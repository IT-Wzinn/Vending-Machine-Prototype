package com.vendingmachine.order.repository;

import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByPublicOrderCode(String publicOrderCode);

    Optional<Order> findByTransferReference(String transferReference);

    boolean existsByPublicOrderCode(String publicOrderCode);

    boolean existsByTransferReference(String transferReference);

    List<Order> findByStatusAndPaymentExpiresAtBefore(OrderStatus status, Instant cutoff);

    /**
     * Atomic state transition: ensures only one thread successfully transitions the order
     * from expectedStatus to newStatus, preventing double-release of reserved stock.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :now WHERE o.id = :id AND o.status = :expectedStatus")
    int updateOrderStatusIfExpected(@Param("id") UUID id,
                                   @Param("expectedStatus") OrderStatus expectedStatus,
                                   @Param("newStatus") OrderStatus newStatus,
                                   @Param("now") Instant now);

    /**
     * Atomic payment confirmation: transitions PENDING_PAYMENT to PAID and records paidAt.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.vendingmachine.order.entity.OrderStatus.PAID, o.paidAt = :paidAt, o.updatedAt = :now WHERE o.id = :id AND o.status = com.vendingmachine.order.entity.OrderStatus.PENDING_PAYMENT")
    int markOrderAsPaidIfPending(@Param("id") UUID id,
                                 @Param("paidAt") Instant paidAt,
                                 @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.transferReference = :transferReference")
    Optional<Order> findByTransferReferenceForUpdate(@Param("transferReference") String transferReference);
}
