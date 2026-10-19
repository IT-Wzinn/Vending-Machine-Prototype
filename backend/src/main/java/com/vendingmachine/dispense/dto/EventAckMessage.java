package com.vendingmachine.dispense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventAckMessage {

    private UUID ackId;
    private UUID eventId;
    private UUID commandId;
    private String machineId;
    private UUID orderId;
    private String status = "PROCESSED";
    private Instant timestamp;

    public EventAckMessage() {
    }

    public EventAckMessage(UUID ackId, UUID eventId, UUID commandId, String machineId, UUID orderId, String status, Instant timestamp) {
        this.ackId = ackId != null ? ackId : UUID.randomUUID();
        this.eventId = eventId;
        this.commandId = commandId;
        this.machineId = machineId;
        this.orderId = orderId;
        this.status = status != null ? status : "PROCESSED";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public UUID getAckId() { return ackId; }
    public void setAckId(UUID ackId) { this.ackId = ackId; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
