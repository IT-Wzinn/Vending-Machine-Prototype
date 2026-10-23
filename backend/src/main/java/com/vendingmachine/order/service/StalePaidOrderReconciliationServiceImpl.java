package com.vendingmachine.order.service;

import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.dispense.recovery.tagger-enabled", havingValue = "true", matchIfMissing = true)
public class StalePaidOrderReconciliationServiceImpl implements StalePaidOrderReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(StalePaidOrderReconciliationServiceImpl.class);

    private final OrderRepository orderRepository;
    private final DispenseCommandRepository dispenseCommandRepository;
    private final ObjectProvider<StalePaidOrderReconciliationService> selfProvider;

    @Value("${app.dispense.recovery.max-age-minutes:15}")
    private int maxAgeMinutes;

    public StalePaidOrderReconciliationServiceImpl(OrderRepository orderRepository,
                                                   DispenseCommandRepository dispenseCommandRepository,
                                                   ObjectProvider<StalePaidOrderReconciliationService> selfProvider) {
        this.orderRepository = orderRepository;
        this.dispenseCommandRepository = dispenseCommandRepository;
        this.selfProvider = selfProvider;
    }

    @Override
    @Scheduled(fixedDelayString = "${app.dispense.recovery.tagger-interval-ms:60000}")
    public int tagStalePaidOrdersForReconciliation() {
        Instant now = Instant.now();
        Instant maxAgeCutoff = now.minus(maxAgeMinutes, ChronoUnit.MINUTES);

        List<UUID> staleOrderIds;
        try {
            staleOrderIds = orderRepository.findStalePaidOrderIdsForReconciliation(maxAgeCutoff);
        } catch (Exception e) {
            log.error("Failed to query stale PAID orders for reconciliation: {}", e.getMessage(), e);
            return 0;
        }

        if (staleOrderIds.isEmpty()) {
            return 0;
        }

        log.info("Found {} stale PAID order(s) exceeding max recovery age ({}m) with no dispense command",
                staleOrderIds.size(), maxAgeMinutes);

        int taggedCount = 0;
        StalePaidOrderReconciliationService proxy = selfProvider.getIfAvailable();
        for (UUID orderId : staleOrderIds) {
            try {
                boolean tagged = proxy != null
                        ? proxy.tagSingleOrderIfEligible(orderId)
                        : tagSingleOrderIfEligible(orderId);
                if (tagged) {
                    taggedCount++;
                }
            } catch (Exception e) {
                // Isolate failure to this single order, keeping loop resilient for remaining orders
                log.error("Failed to process stale order {} for reconciliation: {}", orderId, e.getMessage(), e);
            }
        }

        if (taggedCount > 0) {
            log.info("Stale paid order reconciliation tagger run finished: {}/{} orders transitioned to NEEDS_RECONCILIATION",
                    taggedCount, staleOrderIds.size());
        }

        return taggedCount;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tagSingleOrderIfEligible(UUID orderId) {
        if (orderId == null) {
            return false;
        }

        // 1. Pessimistic write lock to serialize concurrent access
        Optional<Order> orderOpt = orderRepository.findByIdForUpdate(orderId);
        if (orderOpt.isEmpty()) {
            log.warn("Order {} not found during stale paid order reconciliation", orderId);
            return false;
        }

        Order order = orderOpt.get();

        // 2. Safety Guard 1: Must be in PAID status
        if (order.getStatus() != OrderStatus.PAID) {
            log.info("Order {} is no longer in PAID status (current: {}); skipping reconciliation tagging",
                    orderId, order.getStatus());
            return false;
        }

        // 3. Safety Guard 2: Must have valid paidAt strictly before maxAgeCutoff
        Instant now = Instant.now();
        Instant maxAgeCutoff = now.minus(maxAgeMinutes, ChronoUnit.MINUTES);
        if (order.getPaidAt() == null || !order.getPaidAt().isBefore(maxAgeCutoff)) {
            log.info("Order {} paidAt ({}) is not strictly older than cutoff ({}); skipping reconciliation tagging",
                    orderId, order.getPaidAt(), maxAgeCutoff);
            return false;
        }

        // 4. Safety Guard 3: Must NOT have any dispense command
        if (dispenseCommandRepository.existsByOrderId(orderId)) {
            log.info("Order {} already has a dispense command; skipping reconciliation tagging", orderId);
            return false;
        }

        // 5. State transition: PAID -> NEEDS_RECONCILIATION
        order.setStatus(OrderStatus.NEEDS_RECONCILIATION);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        long orderAgeMinutes = ChronoUnit.MINUTES.between(order.getPaidAt(), now);

        // Structured audit log without exposing sensitive secrets
        log.warn("STALE_PAID_ORDER_RECONCILIATION: Tagged order for manual reconciliation | " +
                "orderId={} | publicOrderCode={} | previousStatus={} | newStatus={} | " +
                "paidAt={} | orderAgeMinutes={}m | thresholdMinutes={}m | reason='{}' | processedAt={}",
                order.getId(),
                order.getPublicOrderCode(),
                OrderStatus.PAID,
                OrderStatus.NEEDS_RECONCILIATION,
                order.getPaidAt(),
                orderAgeMinutes,
                maxAgeMinutes,
                "Exceeded recovery max-age with no dispense command",
                now);

        return true;
    }
}
