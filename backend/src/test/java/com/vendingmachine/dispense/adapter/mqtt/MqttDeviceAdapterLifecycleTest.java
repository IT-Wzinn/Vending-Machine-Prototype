package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MqttDeviceAdapterLifecycleTest {

    private MqttProperties properties;
    private ObjectMapper objectMapper;
    private MqttClient mockMqttClient;
    private MqttDeviceAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new MqttProperties();
        properties.setEnabled(true);
        properties.setBrokerUrl("ssl://test-cluster.emqx.cloud:8883");
        properties.setClientId("backend-test-client");
        properties.setMachineId("VM001");
        properties.setCleanSession(false);
        properties.setHeartbeatTimeoutSeconds(120);
        properties.setRequireHeartbeatForOnline(true);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMqttClient = mock(MqttClient.class);
        adapter = new MqttDeviceAdapter(properties, objectMapper, mockMqttClient);
    }

    @Test
    @DisplayName("stop() calls close(true) even when client is disconnected, without calling disconnectForcibly")
    void testStop_whenClientNotConnected_callsCloseTrueWithoutDisconnectForcibly() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(false);

        adapter.stop();

        // Must NOT call disconnectForcibly if client is already disconnected
        verify(mockMqttClient, never()).disconnectForcibly(anyLong());
        // Must ALWAYS call close(true) to halt Paho internal background reconnect threads
        verify(mockMqttClient).close(true);
    }

    @Test
    @DisplayName("stop() calls disconnectForcibly(1000) then close(true) in order when client is connected")
    void testStop_whenClientConnected_callsDisconnectForciblyThenCloseTrue() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        adapter.stop();

        // Must call disconnectForcibly before close(true)
        InOrder inOrder = inOrder(mockMqttClient);
        inOrder.verify(mockMqttClient).disconnectForcibly(1000L);
        inOrder.verify(mockMqttClient).close(true);
    }

    @Test
    @DisplayName("stop() calls close(true) even if disconnectForcibly throws an exception")
    void testStop_whenDisconnectForciblyThrowsException_stillCallsCloseTrue() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);
        doThrow(new MqttException(32100)).when(mockMqttClient).disconnectForcibly(anyLong());

        adapter.stop();

        // Verify disconnectForcibly was attempted and close(true) was still invoked
        verify(mockMqttClient).disconnectForcibly(1000L);
        verify(mockMqttClient).close(true);
    }

    @Test
    @DisplayName("Initial connection subscribes to topics only once via connectComplete(false, ...)")
    void testInitialConnection_subscribesOnlyOnce() throws Exception {
        // Initial state before connect: client is not connected
        when(mockMqttClient.isConnected()).thenReturn(false);

        // When connect() is invoked, simulate connection succeeding (isConnected becomes true)
        // and Paho triggering connectComplete(false, uri)
        doAnswer(inv -> {
            when(mockMqttClient.isConnected()).thenReturn(true);
            adapter.connectComplete(false, "ssl://test-cluster.emqx.cloud:8883");
            return null;
        }).when(mockMqttClient).connect(any(MqttConnectOptions.class));

        adapter.connect();

        // Verify connect was called once
        verify(mockMqttClient, times(1)).connect(any(MqttConnectOptions.class));

        // Verify topics are subscribed exactly once across the entire initial connection sequence
        verify(mockMqttClient, times(1)).subscribe("vending/VM001/events", 1);
        verify(mockMqttClient, times(1)).subscribe("vending/VM001/heartbeat", 0);
    }

    @Test
    @DisplayName("Reconnect via connectComplete(true, ...) re-subscribes to topics")
    void testReconnect_subscribesToTopicsAgain() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        // Simulate Paho automatic reconnection invoking connectComplete(true, uri)
        adapter.connectComplete(true, "ssl://test-cluster.emqx.cloud:8883");

        verify(mockMqttClient, times(1)).subscribe("vending/VM001/events", 1);
        verify(mockMqttClient, times(1)).subscribe("vending/VM001/heartbeat", 0);
    }

    @Test
    @DisplayName("connectionLost handles null cause and MqttException without error")
    void testConnectionLost_handling() {
        assertThatNoException().isThrownBy(() -> {
            adapter.connectionLost(new MqttException(32109));
            adapter.connectionLost(new RuntimeException("Connection reset by peer"));
            adapter.connectionLost(null);
        });
    }
}
