package com.vendingmachine.dispense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceEventMessage {

    private UUID eventId;
    private UUID commandId;
    private String machineId;
    private UUID orderId;
    private String eventType; // COMMAND_ACCEPTED, COMMAND_REJECTED, DISPENSE_RESULT
    private Integer slotNumber;
    private String rejectionReason;
    private String result; // SUCCESS, FAILURE, UNCERTAIN
    private String verificationLevel; // MOTOR_CYCLE_ONLY, SENSOR_CONFIRMED
    private String failureCode;
    private String failureMessage;
    private Instant timestamp;

    public DeviceEventMessage() {
    }

    public static DeviceEventMessage accepted(UUID eventId, UUID commandId, String machineId, UUID orderId, Integer slotNumber, Instant timestamp) {
        DeviceEventMessage msg = new DeviceEventMessage();
        msg.setEventId(eventId != null ? eventId : UUID.randomUUID());
        msg.setCommandId(commandId);
        msg.setMachineId(machineId);
        msg.setOrderId(orderId);
        msg.setEventType("COMMAND_ACCEPTED");
        msg.setSlotNumber(slotNumber);
        msg.setTimestamp(timestamp != null ? timestamp : Instant.now());
        return msg;
    }

    public static DeviceEventMessage rejected(UUID eventId, UUID commandId, String machineId, UUID orderId, Integer slotNumber, String rejectionReason, Instant timestamp) {
        DeviceEventMessage msg = new DeviceEventMessage();
        msg.setEventId(eventId != null ? eventId : UUID.randomUUID());
        msg.setCommandId(commandId);
        msg.setMachineId(machineId);
        msg.setOrderId(orderId);
        msg.setEventType("COMMAND_REJECTED");
        msg.setSlotNumber(slotNumber);
        msg.setRejectionReason(rejectionReason);
        msg.setTimestamp(timestamp != null ? timestamp : Instant.now());
        return msg;
    }

    public static DeviceEventMessage result(UUID eventId, UUID commandId, String machineId, UUID orderId, Integer slotNumber,
                                            String result, String verificationLevel, String failureCode, String failureMessage, Instant timestamp) {
        DeviceEventMessage msg = new DeviceEventMessage();
        msg.setEventId(eventId != null ? eventId : UUID.randomUUID());
        msg.setCommandId(commandId);
        msg.setMachineId(machineId);
        msg.setOrderId(orderId);
        msg.setEventType("DISPENSE_RESULT");
        msg.setSlotNumber(slotNumber);
        msg.setResult(result);
        msg.setVerificationLevel(verificationLevel != null ? verificationLevel : "MOTOR_CYCLE_ONLY");
        msg.setFailureCode(failureCode);
        msg.setFailureMessage(failureMessage);
        msg.setTimestamp(timestamp != null ? timestamp : Instant.now());
        return msg;
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Integer getSlotNumber() { return slotNumber; }
    public void setSlotNumber(Integer slotNumber) { this.slotNumber = slotNumber; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getVerificationLevel() { return verificationLevel; }
    public void setVerificationLevel(String verificationLevel) { this.verificationLevel = verificationLevel; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
