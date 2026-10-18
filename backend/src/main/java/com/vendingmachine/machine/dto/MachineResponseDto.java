package com.vendingmachine.machine.dto;

import com.vendingmachine.machine.entity.Machine;

public class MachineResponseDto {

    private String machineId;
    private String name;
    private String status;
    private String message;

    public MachineResponseDto() {
    }

    public MachineResponseDto(String machineId, String name, String status, String message) {
        this.machineId = machineId;
        this.name = name;
        this.status = status;
        this.message = message;
    }

    public static MachineResponseDto fromEntity(Machine machine) {
        return new MachineResponseDto(
                machine.getMachineCode(),
                machine.getName(),
                machine.getStatus().name(),
                machine.getMessage()
        );
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
