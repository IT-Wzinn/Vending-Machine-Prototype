package com.vendingmachine.dispense.adapter;

import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import com.vendingmachine.dispense.dto.EventAckMessage;

/**
 * Port/Adapter interface for physical or simulated device communication.
 * Allows seamless replacement of the local simulator with real ESP32/MQTT
 * or HTTP adapters with zero changes to the core dispensing business logic.
 */
public interface DeviceAdapter {

    /**
     * Check if the target machine device is currently online and reachable.
     */
    boolean isDeviceOnline(String machineId);

    /**
     * Dispatch a dispense command to the device.
     */
    void sendDispenseCommand(DispenseCommandMessage command);

    /**
     * Dispatch application-level event acknowledgment back to the device
     * after durable database persistence of the result.
     */
    void sendEventAck(EventAckMessage ack);

    /**
     * Identifier for the adapter implementation (e.g. "SIMULATED", "MQTT").
     */
    String getAdapterType();
}
