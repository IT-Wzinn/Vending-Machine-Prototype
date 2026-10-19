package com.vendingmachine.dispense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DispenseTriggerResponseDto {

    private UUID orderId;
    private UUID commandId;
    private String status;
    private String message;

    public DispenseTriggerResponseDto() {
    }

    public DispenseTriggerResponseDto(UUID orderId, UUID commandId, String status, String message) {
        this.orderId = orderId;
        this.commandId = commandId;
        this.status = status;
        this.message = message;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
