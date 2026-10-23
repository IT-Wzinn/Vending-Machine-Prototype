package com.vendingmachine.dispense.service;

import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
import com.vendingmachine.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.dispense.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class DispenseRecoveryServiceImpl implements DispenseRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(DispenseRecoveryServiceImpl.class);

    private final OrderRepository orderRepository;
    private final DispenseService dispenseService;

    @Value("${app.dispense.recovery.min-age-seconds:30}")
    private int minAgeSeconds;

    @Value("${app.dispense.recovery.max-age-minutes:15}")
    private int maxAgeMinutes;

    public DispenseRecoveryServiceImpl(OrderRepository orderRepository,
                                       DispenseService dispenseService) {
        this.orderRepository = orderRepository;
        this.dispenseService = dispenseService;
    }

    @Override
    @Scheduled(fixedDelayString = "${app.dispense.recovery.interval-ms:10000}")
    public int recoverOrphanedPaidOrders() {
        Instant now = Instant.now();
        Instant minAgeCutoff = now.minusSeconds(minAgeSeconds);
        Instant maxAgeCutoff = now.minus(maxAgeMinutes, ChronoUnit.MINUTES);

        List<UUID> orphanedOrderIds;
        try {
            orphanedOrderIds = orderRepository.findOrphanedPaidOrderIdsForRecovery(minAgeCutoff, maxAgeCutoff);
        } catch (Exception e) {
            log.error("Failed to query orphaned PAID orders for recovery: {}", e.getMessage(), e);
            return 0;
        }

        if (orphanedOrderIds.isEmpty()) {
            return 0;
        }

        log.info("Found {} orphaned PAID order(s) eligible for dispense recovery (older than {}s, within last {}m)",
                orphanedOrderIds.size(), minAgeSeconds, maxAgeMinutes);

        int recoveredCount = 0;
        for (UUID orderId : orphanedOrderIds) {
            try {
                log.info("Triggering recovery dispensing for orphaned order {}", orderId);
                DispenseTriggerResponseDto response = dispenseService.triggerDispensing(orderId);
                log.info("Dispense recovery successful for order {}: status={}, message={}",
                        orderId, response.getStatus(), response.getMessage());
                recoveredCount++;
            } catch (Exception e) {
                // Log failure cleanly without exposing sensitive details, keeping scheduler loop resilient
                log.error("Failed to recover orphaned order {}: {}", orderId, e.getMessage(), e);
            }
        }

        if (recoveredCount > 0) {
            log.info("Dispense recovery run finished: {}/{} orders triggered successfully",
                    recoveredCount, orphanedOrderIds.size());
        }

        return recoveredCount;
    }
}
