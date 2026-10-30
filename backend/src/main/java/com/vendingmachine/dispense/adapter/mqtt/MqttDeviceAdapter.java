package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.dispense.adapter.DeviceAdapter;
import com.vendingmachine.dispense.adapter.DeviceEventListener;
import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseCommandMessage;
import com.vendingmachine.dispense.dto.EventAckMessage;
import com.vendingmachine.dispense.dto.HeartbeatMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real MQTT adapter for hardware communication (ESP32) according to docs/MQTT_CONTRACT.md.
 * Active when app.mqtt.enabled=true. Marked as @Primary to override SimulatedDeviceAdapter.
 */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
@Primary
public class MqttDeviceAdapter implements DeviceAdapter, MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceAdapter.class);

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;

    private IMqttClient mqttClient;
    private DeviceEventListener eventListener;

    private final Map<String, Instant> lastHeartbeatByMachine = new ConcurrentHashMap<>();
    private final Map<String, HeartbeatMessage> lastHeartbeatMessageByMachine = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActivityByMachine = new ConcurrentHashMap<>();

    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private ScheduledExecutorService reconnectScheduler;

    @org.springframework.beans.factory.annotation.Autowired
    public MqttDeviceAdapter(MqttProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Testing constructor allowing injection of a mock or custom IMqttClient.
     */
    public MqttDeviceAdapter(MqttProperties properties, ObjectMapper objectMapper, IMqttClient mqttClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mqttClient = mqttClient;
    }

    public void setEventListener(DeviceEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void setMqttClient(IMqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @PostConstruct
    public void start() {
        log.info("[MQTT ADAPTER] Initializing MQTT Device Adapter for broker: {}, clientId: {}, machineId: {}",
                properties.getBrokerUrl(), properties.getClientId(), properties.getMachineId());

        if (mqttClient == null) {
            try {
                mqttClient = new MqttClient(properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            } catch (MqttException e) {
                log.error("[MQTT ADAPTER] Failed to instantiate MqttClient: {}", e.getMessage(), e);
                return;
            }
        }

        try {
            mqttClient.setCallback(this);
        } catch (Exception e) {
            log.warn("[MQTT ADAPTER] Could not register callback on client: {}", e.getMessage());
        }

        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mqtt-reconnect-worker");
            t.setDaemon(true);
            return t;
        });

        // Trigger initial connection
        connect();

        // Schedule periodic health check / reconnect retry in case initial connection failed or dropped
        reconnectScheduler.scheduleWithFixedDelay(this::checkConnectionAndReconnect, 10, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        log.info("[MQTT ADAPTER] Shutting down MQTT Device Adapter");
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
        }

        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnectForcibly(1000);
                mqttClient.close();
            } catch (Exception e) {
                log.warn("[MQTT ADAPTER] Error during MQTT client disconnect: {}", e.getMessage());
            }
        }
    }

    public synchronized void connect() {
        if (mqttClient == null || mqttClient.isConnected() || isConnecting.get()) {
            return;
        }

        isConnecting.set(true);
        try {
            MqttConnectOptions options = buildConnectOptions();
            log.info("[MQTT ADAPTER] Attempting connection to broker {} (cleanSession={})...",
                    properties.getBrokerUrl(), options.isCleanSession());
            mqttClient.connect(options);
            log.info("[MQTT ADAPTER] Connected successfully to MQTT broker!");
            subscribeTopics();
        } catch (MqttException e) {
            log.warn("[MQTT ADAPTER] Initial connection to broker {} failed (ReasonCode: {}): {}. Background retry is active.",
                    properties.getBrokerUrl(), e.getReasonCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[MQTT ADAPTER] Unexpected error while connecting to MQTT broker: {}", e.getMessage(), e);
        } finally {
            isConnecting.set(false);
        }
    }

    private void checkConnectionAndReconnect() {
        if (mqttClient != null && !mqttClient.isConnected()) {
            log.debug("[MQTT ADAPTER] Background check: broker not connected. Retrying connection...");
            connect();
        }
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(properties.isCleanSession());
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        options.setKeepAliveInterval(properties.getKeepAliveSeconds());

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername().trim());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().trim().toCharArray());
        }

        return options;
    }

    private void subscribeTopics() {
        if (mqttClient == null || !mqttClient.isConnected()) {
            return;
        }

        String machineId = properties.getMachineId();
        String eventsTopic = getEventsTopic(machineId);
        String heartbeatTopic = getHeartbeatTopic(machineId);

        try {
            // Subscribe to events topic (QoS 1) and heartbeat topic (QoS 0)
            mqttClient.subscribe(eventsTopic, 1);
            mqttClient.subscribe(heartbeatTopic, 0);
            log.info("[MQTT ADAPTER] Subscribed to topics: '{}' (QoS 1) and '{}' (QoS 0)", eventsTopic, heartbeatTopic);
        } catch (MqttException e) {
            log.error("[MQTT ADAPTER] Failed to subscribe to topics: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isDeviceOnline(String machineId) {
        String targetMachineId = (machineId != null && !machineId.isBlank()) ? machineId : properties.getMachineId();

        // 1. MQTT broker must be connected
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.debug("[MQTT ADAPTER] Device {} considered OFFLINE: MQTT client not connected to broker", targetMachineId);
            return false;
        }

        // 2. Strict Heartbeat Verification
        if (properties.isRequireHeartbeatForOnline()) {
            Instant lastHeartbeat = lastHeartbeatByMachine.get(targetMachineId);
            if (lastHeartbeat == null) {
                // Check if any recent device activity (event received) can act as proof of life
                Instant lastActivity = lastActivityByMachine.get(targetMachineId);
                if (lastActivity != null && Instant.now().minusSeconds(properties.getHeartbeatTimeoutSeconds()).isBefore(lastActivity)) {
                    return true;
                }
                log.debug("[MQTT ADAPTER] Device {} considered OFFLINE: No heartbeat ever received", targetMachineId);
                return false;
            }

            Instant cutoff = Instant.now().minusSeconds(properties.getHeartbeatTimeoutSeconds());
            boolean fresh = cutoff.isBefore(lastHeartbeat);
            if (!fresh) {
                log.warn("[MQTT ADAPTER] Device {} considered OFFLINE: Last heartbeat was at {} (older than {}s timeout)",
                        targetMachineId, lastHeartbeat, properties.getHeartbeatTimeoutSeconds());
            }
            return fresh;
        }

        // Relaxed mode (e.g. dev/testing without heartbeat generator)
        return true;
    }

    @Override
    public void sendDispenseCommand(DispenseCommandMessage command) {
        if (command == null) {
            log.warn("[MQTT ADAPTER] Attempted to send null dispense command");
            return;
        }

        if (mqttClient == null || !mqttClient.isConnected()) {
            log.error("[MQTT ADAPTER] Cannot send dispense command {}: MQTT broker not connected!", command.getCommandId());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "MQTT Broker không kết nối. Không thể phát lệnh xuất hàng.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        String machineId = command.getMachineId() != null ? command.getMachineId() : properties.getMachineId();
        String topic = getCommandsTopic(machineId);

        try {
            String payload = objectMapper.writeValueAsString(command);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1); // QoS 1 as mandated by MQTT Contract §11
            message.setRetained(false); // MUST NOT be retained

            log.info("[MQTT ADAPTER] Publishing dispense command {} to topic '{}' (QoS 1, Slot {})",
                    command.getCommandId(), topic, command.getSlotNumber());
            mqttClient.publish(topic, message);

        } catch (Exception e) {
            log.error("[MQTT ADAPTER] Failed to publish dispense command {} to topic {}: {}",
                    command.getCommandId(), topic, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Lỗi khi gửi lệnh xuất hàng qua MQTT: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void sendEventAck(EventAckMessage ack) {
        if (ack == null) {
            log.warn("[MQTT ADAPTER] Attempted to send null event ack");
            return;
        }

        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("[MQTT ADAPTER] Cannot send event ack {}: MQTT broker not connected", ack.getAckId());
            return;
        }

        String machineId = ack.getMachineId() != null ? ack.getMachineId() : properties.getMachineId();
        String topic = getEventsAckTopic(machineId);

        try {
            String payload = objectMapper.writeValueAsString(ack);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1); // QoS 1 as mandated by MQTT Contract §11
            message.setRetained(false);

            log.info("[MQTT ADAPTER] Publishing event ACK {} for eventId {} to topic '{}'",
                    ack.getAckId(), ack.getEventId(), topic);
            mqttClient.publish(topic, message);

        } catch (Exception e) {
            log.error("[MQTT ADAPTER] Failed to publish event ACK {} to topic {}: {}",
                    ack.getAckId(), topic, e.getMessage(), e);
        }
    }

    @Override
    public String getAdapterType() {
        return "MQTT";
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // MqttCallbackExtended Implementation
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("[MQTT ADAPTER] Connection complete to {} (reconnect={})", serverURI, reconnect);
        subscribeTopics();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[MQTT ADAPTER] MQTT connection lost: {}", (cause != null ? cause.getMessage() : "Unknown reason"));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("[MQTT ADAPTER] Message arrived on topic '{}': {}", topic, payload);

        try {
            if (topic.endsWith("/events")) {
                handleDeviceEventPayload(payload);
            } else if (topic.endsWith("/heartbeat")) {
                handleHeartbeatPayload(payload);
            } else {
                log.warn("[MQTT ADAPTER] Received message on unhandled topic '{}'", topic);
            }
        } catch (Exception e) {
            log.error("[MQTT ADAPTER] Error processing message on topic '{}': {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("[MQTT ADAPTER] Message delivery completed (messageId={})", token.getMessageId());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Message Dispatching & State Tracking
    // ─────────────────────────────────────────────────────────────────────────────

    private void handleDeviceEventPayload(String payload) {
        try {
            DeviceEventMessage event = objectMapper.readValue(payload, DeviceEventMessage.class);
            if (event.getMachineId() != null) {
                lastActivityByMachine.put(event.getMachineId(), Instant.now());
            }

            log.info("[MQTT ADAPTER] Inbound Device Event: type={}, eventId={}, commandId={}, result={}",
                    event.getEventType(), event.getEventId(), event.getCommandId(), event.getResult());

            if (eventListener != null) {
                eventListener.onDeviceEvent(event);
            } else {
                log.warn("[MQTT ADAPTER] No DeviceEventListener registered! Dropping device event {}", event.getEventId());
            }
        } catch (Exception e) {
            log.error("[MQTT ADAPTER] Failed to deserialize DeviceEventMessage: {}", e.getMessage(), e);
        }
    }

    private void handleHeartbeatPayload(String payload) {
        try {
            HeartbeatMessage heartbeat = objectMapper.readValue(payload, HeartbeatMessage.class);
            recordHeartbeat(heartbeat);
        } catch (Exception e) {
            log.error("[MQTT ADAPTER] Failed to deserialize HeartbeatMessage: {}", e.getMessage(), e);
        }
    }

    public void recordHeartbeat(HeartbeatMessage heartbeat) {
        if (heartbeat == null || heartbeat.getMachineId() == null) {
            return;
        }
        String machineId = heartbeat.getMachineId();
        Instant now = Instant.now();
        lastHeartbeatByMachine.put(machineId, now);
        lastHeartbeatMessageByMachine.put(machineId, heartbeat);
        lastActivityByMachine.put(machineId, now);

        log.info("[MQTT HEARTBEAT] Machine '{}' online: FW={}, uptime={}s, RSSI={}dBm, reconnects={}",
                machineId, heartbeat.getFirmwareVersion(), heartbeat.getUptimeSeconds(),
                heartbeat.getWifiRssi(), heartbeat.getMqttReconnectCount());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Topic Helpers (docs/MQTT_CONTRACT.md §3)
    // ─────────────────────────────────────────────────────────────────────────────

    public String getCommandsTopic(String machineId) {
        return "vending/" + machineId + "/commands";
    }

    public String getEventsTopic(String machineId) {
        return "vending/" + machineId + "/events";
    }

    public String getEventsAckTopic(String machineId) {
        return "vending/" + machineId + "/events/ack";
    }

    public String getHeartbeatTopic(String machineId) {
        return "vending/" + machineId + "/heartbeat";
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Getters for Inspection & Testing
    // ─────────────────────────────────────────────────────────────────────────────

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public HeartbeatMessage getLastHeartbeatMessage(String machineId) {
        return lastHeartbeatMessageByMachine.get(machineId);
    }

    public Instant getLastHeartbeatTime(String machineId) {
        return lastHeartbeatByMachine.get(machineId);
    }
}
