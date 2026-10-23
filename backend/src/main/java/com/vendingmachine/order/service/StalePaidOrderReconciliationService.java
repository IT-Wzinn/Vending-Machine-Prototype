package com.vendingmachine.order.service;

import java.util.UUID;

public interface StalePaidOrderReconciliationService {

    /**
     * Scan and tag stale PAID orders that have exceeded the recovery max-age
     * with no dispense commands, transitioning them to NEEDS_RECONCILIATION.
     *
     * @return number of stale orders tagged for reconciliation
     */
    int tagStalePaidOrdersForReconciliation();

    /**
     * Process an individual order for reconciliation in its own transaction (REQUIRES_NEW)
     * with pessimistic write locking.
     *
     * @param orderId the order ID to tag
     * @return true if the order was transitioned, false otherwise
     */
    boolean tagSingleOrderIfEligible(UUID orderId);
}
