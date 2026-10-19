package com.vendingmachine.dispense.adapter.simulated;

public enum SimulationScenario {
    /**
     * Deterministic success: sends COMMAND_ACCEPTED followed by DISPENSE_RESULT(SUCCESS).
     */
    SUCCESS,

    /**
     * Deterministic failure: sends COMMAND_ACCEPTED followed by DISPENSE_RESULT(FAILURE, MOTOR_TIMEOUT).
     */
    FAILURE,

    /**
     * Device offline: isDeviceOnline returns false; rejects command dispatch.
     */
    OFFLINE,

    /**
     * Command timeout: device sends COMMAND_ACCEPTED but never sends DISPENSE_RESULT.
     */
    TIMEOUT,

    /**
     * Uncertain outcome: simulates device reboot during motor execution.
     * Sends COMMAND_ACCEPTED followed by DISPENSE_RESULT(UNCERTAIN, REBOOT_DURING_EXECUTION).
     */
    UNCERTAIN,

    /**
     * Duplicate command simulation: rejects command with DUPLICATE_COMMAND.
     */
    DUPLICATE
}
