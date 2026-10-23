package com.vendingmachine.dispense.service;

public interface DispenseRecoveryService {

    /**
     * Scan and recover orphaned PAID orders that have not triggered dispensing
     * within the safe configured age window.
     *
     * @return number of orphaned orders successfully recovered
     */
    int recoverOrphanedPaidOrders();
}
