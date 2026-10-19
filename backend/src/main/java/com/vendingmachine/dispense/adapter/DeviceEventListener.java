package com.vendingmachine.dispense.adapter;

import com.vendingmachine.dispense.dto.DeviceEventMessage;

/**
 * Callback listener for events originating from the device layer (e.g. ESP32 or simulator).
 */
@FunctionalInterface
public interface DeviceEventListener {

    /**
     * Handle incoming device event (COMMAND_ACCEPTED, COMMAND_REJECTED, DISPENSE_RESULT).
     */
    void onDeviceEvent(DeviceEventMessage event);
}
