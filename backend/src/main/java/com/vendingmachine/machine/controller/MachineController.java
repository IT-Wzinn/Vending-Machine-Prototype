package com.vendingmachine.machine.controller;

import com.vendingmachine.common.ApiResponse;
import com.vendingmachine.machine.dto.MachineResponseDto;
import com.vendingmachine.machine.service.MachineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/machines")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping("/{machineId}")
    public ResponseEntity<ApiResponse<MachineResponseDto>> getMachine(@PathVariable String machineId) {
        MachineResponseDto dto = machineService.getMachineByCode(machineId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }
}
