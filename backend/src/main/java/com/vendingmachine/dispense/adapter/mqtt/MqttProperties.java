package com.vendingmachine.dispense.adapter.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {

    /**
     * Whether MQTT transport is enabled. When false, Simulator mode is used.
     */
    private boolean enabled = false;

    /**
     * MQTT Broker URL (e.g. ssl://cluster.s1.eu.hivemq.cloud:8883 or tcp://localhost:1883).
     */
    private String brokerUrl = "ssl://localhost:8883";

    /**
     * MQTT Client ID used by the backend.
     */
    private String clientId = "backend-vending-001";

    /**
     * MQTT Username for broker authentication.
     */
    private String username;

    /**
     * MQTT Password for broker authentication.
     */
    private String password;

    /**
     * Machine ID to subscribe/publish to (e.g. VM001).
     */
    private String machineId = "VM001";

    /**
     * Connection timeout in seconds.
     */
    private int connectionTimeoutSeconds = 30;

    /**
     * Keep-alive ping interval in seconds.
     */
    private int keepAliveSeconds = 60;

    /**
     * Clean session flag. Contract §2.3 mandates false (persistent session) for Backend.
     */
    private boolean cleanSession = false;

    /**
     * Maximum allowed duration in seconds since last heartbeat before marking device OFFLINE.
     */
    private int heartbeatTimeoutSeconds = 120;

    /**
     * Whether device online check strictly requires a recent heartbeat.
     * When true, isDeviceOnline() returns true ONLY if a heartbeat arrived within heartbeatTimeoutSeconds.
     */
    private boolean requireHeartbeatForOnline = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public int getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public boolean isRequireHeartbeatForOnline() {
        return requireHeartbeatForOnline;
    }

    public void setRequireHeartbeatForOnline(boolean requireHeartbeatForOnline) {
        this.requireHeartbeatForOnline = requireHeartbeatForOnline;
    }
}
