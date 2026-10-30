package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.dispense.adapter.DeviceEventListener;
import com.vendingmachine.dispense.dto.*;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MqttDeviceAdapterTest {

    private MqttProperties properties;
    private ObjectMapper objectMapper;
    private IMqttClient mockMqttClient;
    private MqttDeviceAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new MqttProperties();
        properties.setEnabled(true);
        properties.setBrokerUrl("ssl://test-cluster.hivemq.cloud:8883");
        properties.setClientId("backend-test-client");
        properties.setMachineId("VM001");
        properties.setCleanSession(false);
        properties.setHeartbeatTimeoutSeconds(120);
        properties.setRequireHeartbeatForOnline(true);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMqttClient = mock(IMqttClient.class);
        adapter = new MqttDeviceAdapter(properties, objectMapper, mockMqttClient);
    }

    @Test
    @DisplayName("Adapter type should be MQTT")
    void testAdapterType() {
        assertThat(adapter.getAdapterType()).isEqualTo("MQTT");
    }

    @Test
    @DisplayName("Topic paths match MQTT Contract v1.1 specifications")
    void testTopicConventions() {
        assertThat(adapter.getCommandsTopic("VM001")).isEqualTo("vending/VM001/commands");
        assertThat(adapter.getEventsTopic("VM001")).isEqualTo("vending/VM001/events");
        assertThat(adapter.getEventsAckTopic("VM001")).isEqualTo("vending/VM001/events/ack");
        assertThat(adapter.getHeartbeatTopic("VM001")).isEqualTo("vending/VM001/heartbeat");
    }

    @Test
    @DisplayName("sendDispenseCommand publishes to vending/{machineId}/commands with QoS 1 and not retained")
    void testSendDispenseCommandSuccess() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        UUID commandId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        DispenseCommandMessage cmd = new DispenseCommandMessage(
                commandId, "VM001", orderId, 2, now, now.plusSeconds(45), Map.of("motorRunMs", 800)
        );

        adapter.sendDispenseCommand(cmd);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);

        verify(mockMqttClient).publish(topicCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("vending/VM001/commands");
        MqttMessage message = messageCaptor.getValue();
        assertThat(message.getQos()).isEqualTo(1);
        assertThat(message.isRetained()).isFalse();

        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        assertThat(payload).contains(commandId.toString());
        assertThat(payload).contains("VM001");
        assertThat(payload).contains(orderId.toString());
        assertThat(payload).contains("\"slotNumber\":2");
        assertThat(payload).contains("\"action\":\"DISPENSE\"");
    }

    @Test
    @DisplayName("sendDispenseCommand throws BusinessException when MQTT broker is disconnected")
    void testSendDispenseCommandBrokerDisconnected() {
        when(mockMqttClient.isConnected()).thenReturn(false);

        DispenseCommandMessage cmd = new DispenseCommandMessage(
                UUID.randomUUID(), "VM001", UUID.randomUUID(), 1, Instant.now(), Instant.now().plusSeconds(45), Map.of()
        );

        assertThatThrownBy(() -> adapter.sendDispenseCommand(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MQTT Broker không kết nối");
    }

    @Test
    @DisplayName("sendEventAck publishes to vending/{machineId}/events/ack with QoS 1")
    void testSendEventAckSuccess() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        UUID ackId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        EventAckMessage ack = new EventAckMessage(ackId, eventId, commandId, "VM001", orderId, "PROCESSED", Instant.now());

        adapter.sendEventAck(ack);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);

        verify(mockMqttClient).publish(topicCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("vending/VM001/events/ack");
        MqttMessage message = messageCaptor.getValue();
        assertThat(message.getQos()).isEqualTo(1);
        assertThat(message.isRetained()).isFalse();

        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        assertThat(payload).contains(ackId.toString());
        assertThat(payload).contains(eventId.toString());
        assertThat(payload).contains("\"status\":\"PROCESSED\"");
    }

    @Test
    @DisplayName("isDeviceOnline correctly distinguishes broker connection vs physical device heartbeat")
    void testIsDeviceOnlineDistinction() {
        // 1. Broker disconnected -> always offline
        when(mockMqttClient.isConnected()).thenReturn(false);
        assertThat(adapter.isDeviceOnline("VM001")).isFalse();

        // 2. Broker connected, but requireHeartbeatForOnline=true and no heartbeat received -> offline
        when(mockMqttClient.isConnected()).thenReturn(true);
        assertThat(adapter.isDeviceOnline("VM001")).isFalse();

        // 3. Heartbeat received -> online
        HeartbeatMessage hb = new HeartbeatMessage(
                "VM001", "1.0.0", 3600L, -65, 0,
                List.of(new SlotStatusDto(1, "SUCCESS", Instant.now())), Instant.now()
        );
        adapter.recordHeartbeat(hb);
        assertThat(adapter.isDeviceOnline("VM001")).isTrue();

        // 4. Relaxed mode (requireHeartbeatForOnline=false) -> online as long as broker connected
        properties.setRequireHeartbeatForOnline(false);
        assertThat(adapter.isDeviceOnline("VM999")).isTrue();
    }

    @Test
    @DisplayName("messageArrived on events topic forwards parsed event to DeviceEventListener")
    void testMessageArrivedEventForwarding() throws Exception {
        AtomicReference<DeviceEventMessage> receivedEvent = new AtomicReference<>();
        DeviceEventListener listener = receivedEvent::set;
        adapter.setEventListener(listener);

        UUID eventId = UUID.randomUUID();
        UUID cmdId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        DeviceEventMessage acceptedMsg = DeviceEventMessage.accepted(
                eventId, cmdId, "VM001", orderId, 2, Instant.now()
        );
        String payload = objectMapper.writeValueAsString(acceptedMsg);

        MqttMessage mqttMessage = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        adapter.messageArrived("vending/VM001/events", mqttMessage);

        assertThat(receivedEvent.get()).isNotNull();
        assertThat(receivedEvent.get().getEventId()).isEqualTo(eventId);
        assertThat(receivedEvent.get().getEventType()).isEqualTo("COMMAND_ACCEPTED");
        assertThat(receivedEvent.get().getSlotNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("messageArrived on heartbeat topic records machine health and slots")
    void testMessageArrivedHeartbeatProcessing() throws Exception {
        HeartbeatMessage hb = new HeartbeatMessage(
                "VM001", "1.0.0", 7200L, -58, 1,
                List.of(
                        new SlotStatusDto(1, "SUCCESS", Instant.now()),
                        new SlotStatusDto(2, null, null),
                        new SlotStatusDto(3, null, null)
                ),
                Instant.now()
        );
        String payload = objectMapper.writeValueAsString(hb);

        MqttMessage mqttMessage = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        adapter.messageArrived("vending/VM001/heartbeat", mqttMessage);

        when(mockMqttClient.isConnected()).thenReturn(true);
        assertThat(adapter.isDeviceOnline("VM001")).isTrue();
        HeartbeatMessage recorded = adapter.getLastHeartbeatMessage("VM001");
        assertThat(recorded).isNotNull();
        assertThat(recorded.getFirmwareVersion()).isEqualTo("1.0.0");
        assertThat(recorded.getWifiRssi()).isEqualTo(-58);
        assertThat(recorded.getUptimeSeconds()).isEqualTo(7200L);
    }
}
