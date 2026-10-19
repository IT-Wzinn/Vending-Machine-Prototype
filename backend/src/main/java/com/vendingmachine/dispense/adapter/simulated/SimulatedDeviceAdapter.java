package com.vendingmachine.dispense.adapter.simulated;

import com.vendingmachine.dispense.adapter.DeviceAdapter;
import com.vendingmachine.dispense.adapter.DeviceEventListener;
import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import com.vendingmachine.dispense.dto.EventAckMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local simulated ESP32 / device adapter for testing without physical hardware.
 * Clearly labelled as SIMULATION; never asserts physical dispensing.
 */
@Component
public class SimulatedDeviceAdapter implements DeviceAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedDeviceAdapter.class);

    private final AtomicReference<SimulationScenario> scenario = new AtomicReference<>(SimulationScenario.SUCCESS);
    private final Set<UUID> processedCommandIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> acknowledgedEventIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private DeviceEventListener eventListener;
    private boolean asyncMode = false;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public SimulatedDeviceAdapter(
            @Value("${app.dispense.simulator.default-scenario:SUCCESS}") String defaultScenario,
            @Value("${app.dispense.simulator.async:false}") boolean asyncMode) {
        try {
            this.scenario.set(SimulationScenario.valueOf(defaultScenario.toUpperCase()));
        } catch (Exception e) {
            this.scenario.set(SimulationScenario.SUCCESS);
        }
        this.asyncMode = asyncMode;
        log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Initialized with scenario: {}, asyncMode: {}",
                this.scenario.get(), this.asyncMode);
    }

    public void setEventListener(DeviceEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void setScenario(SimulationScenario newScenario) {
        log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Switching scenario from {} to {}", this.scenario.get(), newScenario);
        this.scenario.set(newScenario);
    }

    public SimulationScenario getScenario() {
        return this.scenario.get();
    }

    public void setAsyncMode(boolean asyncMode) {
        this.asyncMode = asyncMode;
    }

    public boolean isAsyncMode() {
        return asyncMode;
    }

    public void resetState() {
        processedCommandIds.clear();
        acknowledgedEventIds.clear();
        log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Reset simulator NVS state");
    }

    public Set<UUID> getProcessedCommandIds() {
        return Collections.unmodifiableSet(processedCommandIds);
    }

    public Set<UUID> getAcknowledgedEventIds() {
        return Collections.unmodifiableSet(acknowledgedEventIds);
    }

    @Override
    public boolean isDeviceOnline(String machineId) {
        boolean online = scenario.get() != SimulationScenario.OFFLINE;
        log.debug("[SIMULATED ESP32 - NOT REAL HARDWARE] Checking online status for {}: {}", machineId, online);
        return online;
    }

    @Override
    public void sendDispenseCommand(DispenseCommandMessage command) {
        if (command == null) {
            log.warn("[SIMULATED ESP32 - NOT REAL HARDWARE] Received null dispense command");
            return;
        }

        SimulationScenario currentScenario = this.scenario.get();
        log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Received command {} for machine {}, slot {} under scenario {}",
                command.getCommandId(), command.getMachineId(), command.getSlotNumber(), currentScenario);

        if (currentScenario == SimulationScenario.OFFLINE) {
            log.warn("[SIMULATED ESP32 - NOT REAL HARDWARE] Command dropped: Simulated device is OFFLINE");
            return;
        }

        Runnable task = () -> executeSimulationTask(command, currentScenario);

        if (asyncMode) {
            executorService.submit(task);
        } else {
            task.run();
        }
    }

    private void executeSimulationTask(DispenseCommandMessage command, SimulationScenario currentScenario) {
        UUID commandId = command.getCommandId();

        // Check duplicate simulation
        if (currentScenario == SimulationScenario.DUPLICATE || processedCommandIds.contains(commandId)) {
            log.warn("[SIMULATED ESP32 - NOT REAL HARDWARE] Rejecting duplicate command {}", commandId);
            DeviceEventMessage rejectEvent = DeviceEventMessage.rejected(
                    UUID.randomUUID(),
                    commandId,
                    command.getMachineId(),
                    command.getOrderId(),
                    command.getSlotNumber(),
                    "DUPLICATE_COMMAND",
                    Instant.now()
            );
            notifyListener(rejectEvent);
            return;
        }

        // 1. Send COMMAND_ACCEPTED
        processedCommandIds.add(commandId);
        DeviceEventMessage acceptedEvent = DeviceEventMessage.accepted(
                UUID.randomUUID(),
                commandId,
                command.getMachineId(),
                command.getOrderId(),
                command.getSlotNumber(),
                Instant.now()
        );
        notifyListener(acceptedEvent);

        // 2. Handle outcome based on scenario
        switch (currentScenario) {
            case TIMEOUT:
                log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Simulating TIMEOUT: No result event will be sent for command {}", commandId);
                break;

            case FAILURE:
                log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Simulating FAILURE (MOTOR_TIMEOUT) for command {}", commandId);
                DeviceEventMessage failureEvent = DeviceEventMessage.result(
                        UUID.randomUUID(),
                        commandId,
                        command.getMachineId(),
                        command.getOrderId(),
                        command.getSlotNumber(),
                        "FAILURE",
                        "MOTOR_CYCLE_ONLY",
                        "MOTOR_TIMEOUT",
                        "Motor did not complete cycle within expected time.",
                        Instant.now()
                );
                notifyListener(failureEvent);
                break;

            case UNCERTAIN:
                log.warn("[SIMULATED ESP32 - NOT REAL HARDWARE] Simulating UNCERTAIN (REBOOT_DURING_EXECUTION) for command {}", commandId);
                DeviceEventMessage uncertainEvent = DeviceEventMessage.result(
                        UUID.randomUUID(),
                        commandId,
                        command.getMachineId(),
                        command.getOrderId(),
                        command.getSlotNumber(),
                        "UNCERTAIN",
                        "MOTOR_CYCLE_ONLY",
                        "REBOOT_DURING_EXECUTION",
                        "Device rebooted while motor was in EXECUTING state. Physical outcome unknown.",
                        Instant.now()
                );
                notifyListener(uncertainEvent);
                break;

            case SUCCESS:
            default:
                log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Simulating SUCCESS (MOTOR_CYCLE_ONLY) for command {}", commandId);
                DeviceEventMessage successEvent = DeviceEventMessage.result(
                        UUID.randomUUID(),
                        commandId,
                        command.getMachineId(),
                        command.getOrderId(),
                        command.getSlotNumber(),
                        "SUCCESS",
                        "MOTOR_CYCLE_ONLY",
                        null,
                        null,
                        Instant.now()
                );
                notifyListener(successEvent);
                break;
        }
    }

    @Override
    public void sendEventAck(EventAckMessage ack) {
        if (ack == null) {
            return;
        }
        acknowledgedEventIds.add(ack.getEventId());
        log.info("[SIMULATED ESP32 - NOT REAL HARDWARE] Received EVENT_ACK for eventId: {}, commandId: {}, clearing simulated NVS outbox",
                ack.getEventId(), ack.getCommandId());
    }

    @Override
    public String getAdapterType() {
        return "SIMULATED";
    }

    private void notifyListener(DeviceEventMessage event) {
        if (eventListener != null) {
            try {
                eventListener.onDeviceEvent(event);
            } catch (Exception e) {
                log.error("[SIMULATED ESP32 - NOT REAL HARDWARE] Error dispatching event to listener", e);
            }
        }
    }
}
