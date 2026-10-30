package com.vendingmachine.dispense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeartbeatMessage {

    private String machineId;
    private String firmwareVersion;
    private Long uptimeSeconds;
    private Integer wifiRssi;
    private Integer mqttReconnectCount;
    private List<SlotStatusDto> slots;
    private Instant timestamp;

    public HeartbeatMessage() {
    }

    public HeartbeatMessage(String machineId, String firmwareVersion, Long uptimeSeconds, Integer wifiRssi,
                            Integer mqttReconnectCount, List<SlotStatusDto> slots, Instant timestamp) {
        this.machineId = machineId;
        this.firmwareVersion = firmwareVersion;
        this.uptimeSeconds = uptimeSeconds;
        this.wifiRssi = wifiRssi;
        this.mqttReconnectCount = mqttReconnectCount;
        this.slots = slots;
        this.timestamp = timestamp;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public Long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(Long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public Integer getWifiRssi() {
        return wifiRssi;
    }

    public void setWifiRssi(Integer wifiRssi) {
        this.wifiRssi = wifiRssi;
    }

    public Integer getMqttReconnectCount() {
        return mqttReconnectCount;
    }

    public void setMqttReconnectCount(Integer mqttReconnectCount) {
        this.mqttReconnectCount = mqttReconnectCount;
    }

    public List<SlotStatusDto> getSlots() {
        return slots;
    }

    public void setSlots(List<SlotStatusDto> slots) {
        this.slots = slots;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
