package com.vendingmachine.order.repository;

import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
