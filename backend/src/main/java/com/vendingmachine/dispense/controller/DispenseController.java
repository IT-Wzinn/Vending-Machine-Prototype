package com.vendingmachine.dispense.controller;

import com.vendingmachine.common.ApiResponse;
import com.vendingmachine.dispense.dto.DispenseCommandResponseDto;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
import com.vendingmachine.dispense.service.DispenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispense")
public class DispenseController {

    private final DispenseService dispenseService;

    public DispenseController(DispenseService dispenseService) {
        this.dispenseService = dispenseService;
    }

    /**
     * Trigger dispensing for an order that is in PAID status.
     * Idempotent: if already dispensing or completed, returns existing state.
     */
    @PostMapping("/orders/{orderId}/trigger")
    public ResponseEntity<ApiResponse<DispenseTriggerResponseDto>> triggerDispense(@PathVariable UUID orderId) {
        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(orderId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * Get latest dispense command details for an order.
     */
    @GetMapping("/orders/{orderId}/command")
    public ResponseEntity<ApiResponse<DispenseCommandResponseDto>> getDispenseCommand(@PathVariable UUID orderId) {
        DispenseCommandResponseDto response = dispenseService.getDispenseCommandByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
