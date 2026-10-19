package com.vendingmachine.dispense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DispenseCommandMessage {

    private UUID commandId;
    private String machineId;
    private UUID orderId;
    private Integer slotNumber;
    private String action = "DISPENSE";
    private Instant createdAt;
    private Instant expiresAt;
    private Map<String, Object> parameters;

    public DispenseCommandMessage() {
    }

    public DispenseCommandMessage(UUID commandId, String machineId, UUID orderId, Integer slotNumber,
                                  Instant createdAt, Instant expiresAt, Map<String, Object> parameters) {
        this.commandId = commandId;
        this.machineId = machineId;
        this.orderId = orderId;
        this.slotNumber = slotNumber;
        this.action = "DISPENSE";
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.parameters = parameters;
    }

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public Integer getSlotNumber() { return slotNumber; }
    public void setSlotNumber(Integer slotNumber) { this.slotNumber = slotNumber; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
