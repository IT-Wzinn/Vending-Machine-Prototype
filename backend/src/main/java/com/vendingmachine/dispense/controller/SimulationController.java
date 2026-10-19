package com.vendingmachine.dispense.controller;

import com.vendingmachine.common.ApiResponse;
import com.vendingmachine.dispense.adapter.simulated.SimulatedDeviceAdapter;
import com.vendingmachine.dispense.adapter.simulated.SimulationScenario;
import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.service.DispenseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for inspecting and controlling the simulated device adapter.
 * Clearly labelled as SIMULATION; does not control physical hardware.
 * Strictly restricted to local, dev, and test environments.
 */
@RestController
@RequestMapping("/api/v1/simulation/device")
@ConditionalOnProperty(name = "app.dispense.simulator.enabled", havingValue = "true", matchIfMissing = true)
@Profile({"local", "dev", "test", "test-postgres"})
public class SimulationController {

    private final SimulatedDeviceAdapter simulatedDeviceAdapter;
    private final DispenseService dispenseService;

    public SimulationController(SimulatedDeviceAdapter simulatedDeviceAdapter, DispenseService dispenseService) {
        this.simulatedDeviceAdapter = simulatedDeviceAdapter;
        this.dispenseService = dispenseService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSimulationStatus() {
        Map<String, Object> status = Map.of(
                "adapterType", simulatedDeviceAdapter.getAdapterType(),
                "currentScenario", simulatedDeviceAdapter.getScenario(),
                "asyncMode", simulatedDeviceAdapter.isAsyncMode(),
                "isOnline", simulatedDeviceAdapter.isDeviceOnline("VM001"),
                "processedCommandsCount", simulatedDeviceAdapter.getProcessedCommandIds().size(),
                "acknowledgedEventsCount", simulatedDeviceAdapter.getAcknowledgedEventIds().size(),
                "disclaimer", "SIMULATION ONLY - NOT PHYSICAL HARDWARE"
        );
        return ResponseEntity.ok(ApiResponse.of(status));
    }

    @PostMapping("/mode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setSimulationMode(@RequestBody Map<String, Object> body) {
        String scenarioStr = (String) body.get("scenario");
        if (scenarioStr != null) {
            SimulationScenario scenario = SimulationScenario.valueOf(scenarioStr.toUpperCase());
            simulatedDeviceAdapter.setScenario(scenario);
        }

        if (body.containsKey("async")) {
            boolean async = Boolean.parseBoolean(body.get("async").toString());
            simulatedDeviceAdapter.setAsyncMode(async);
        }

        return getSimulationStatus();
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetSimulationState() {
        simulatedDeviceAdapter.resetState();
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Simulator NVS state cleared successfully.")));
    }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<Map<String, String>>> injectDeviceEvent(@RequestBody DeviceEventMessage event) {
        dispenseService.handleDeviceEvent(event);
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Simulated device event processed.")));
    }
}
