package com.vendingmachine.dispense.service;

import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseCommandResponseDto;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;

import java.util.UUID;

public interface DispenseService {

    /**
     * Trigger dispensing for an order that is in PAID status.
     */
    DispenseTriggerResponseDto triggerDispensing(UUID orderId);

    /**
     * Process an incoming device event (COMMAND_ACCEPTED, COMMAND_REJECTED, DISPENSE_RESULT).
     */
    void handleDeviceEvent(DeviceEventMessage event);

    /**
     * Get the latest dispense command associated with an order.
     */
    DispenseCommandResponseDto getDispenseCommandByOrderId(UUID orderId);

    /**
     * Periodic check to handle stale commands that have timed out.
     */
    int checkAndHandleTimeouts();
}
