package com.vendingmachine.dispense.adapter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
import com.vendingmachine.dispense.dto.HeartbeatMessage;
import com.vendingmachine.dispense.dto.SlotStatusDto;
import com.vendingmachine.dispense.entity.DispenseCommand;
import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.dispense.service.DispenseService;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.repository.MachineRepository;
import com.vendingmachine.order.dto.CreateOrderRequestDto;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.order.service.OrderService;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.mqtt.enabled=true",
        "app.dispense.simulator.enabled=true",
        "app.mqtt.require-heartbeat-for-online=true",
        "app.mqtt.machine-id=VM001"
})
public class MqttDispenseIntegrationTest {

    @Autowired
    private DispenseService dispenseService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private DispenseCommandRepository dispenseCommandRepository;

    @Autowired
    private DeviceEventRepository deviceEventRepository;

    @Autowired
    private MqttDeviceAdapter mqttDeviceAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    private IMqttClient mockMqttClient;

    private Machine testMachine;
    private Product productSlot1;
    private Product productSlot2;
    private Product productSlot3;

    @BeforeEach
    void setUp() {
        mockMqttClient = mock(IMqttClient.class);
        when(mockMqttClient.isConnected()).thenReturn(true);
        mqttDeviceAdapter.setMqttClient(mockMqttClient);

        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();

        testMachine = new Machine(
                UUID.randomUUID(),
                "VM001",
                "Máy bán hàng MQTT Test",
                MachineStatus.ACTIVE,
                null
        );
        testMachine = machineRepository.save(testMachine);

        productSlot1 = productRepository.save(new Product(
                UUID.randomUUID(), testMachine, "Nước khoáng Lavie", "Slot 1",
                "https://example.com/lavie.jpg", 10000, 1, 10, 0, true
        ));

        productSlot2 = productRepository.save(new Product(
                UUID.randomUUID(), testMachine, "Trà Ô Long", "Slot 2",
                "https://example.com/olong.jpg", 15000, 2, 10, 0, true
        ));

        productSlot3 = productRepository.save(new Product(
                UUID.randomUUID(), testMachine, "Cà phê lon Highlands", "Slot 3",
                "https://example.com/highlands.jpg", 20000, 3, 10, 0, true
        ));

        // Inject initial heartbeat so device is recognized as ONLINE
        HeartbeatMessage initialHeartbeat = new HeartbeatMessage(
                "VM001", "1.0.0", 3600L, -60, 0,
                List.of(new SlotStatusDto(1, null, null)), Instant.now()
        );
        mqttDeviceAdapter.recordHeartbeat(initialHeartbeat);
    }

    @AfterEach
    void tearDown() {
        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();
    }

    private Order createAndPayOrder(Product product) {
        OrderResponseDto created = orderService.createOrder(new CreateOrderRequestDto("VM001", product.getId(), 1));
        Order order = orderRepository.findById(created.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("End-to-End MQTT Happy Path: Command -> Accepted -> Dispense Result SUCCESS -> Stock Deducted -> Event ACK")
    void testMqttDispenseHappyPath() throws Exception {
        Order order = createAndPayOrder(productSlot2);
        UUID orderId = order.getId();

        // 1. Trigger Dispensing
        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(orderId);
        assertThat(response.getStatus()).isEqualTo("DISPENSING");
        UUID commandId = response.getCommandId();

        // Verify MQTT Command was published to vending/VM001/commands with QoS 1
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mockMqttClient, atLeastOnce()).publish(topicCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("vending/VM001/commands");
        MqttMessage publishedCmd = messageCaptor.getValue();
        assertThat(publishedCmd.getQos()).isEqualTo(1);
        assertThat(publishedCmd.isRetained()).isFalse();

        String cmdPayload = new String(publishedCmd.getPayload(), StandardCharsets.UTF_8);
        assertThat(cmdPayload).contains(commandId.toString());
        assertThat(cmdPayload).contains("\"slotNumber\":2");
        assertThat(cmdPayload).contains("\"machineId\":\"VM001\"");

        // Check DB DispenseCommand state
        DispenseCommand dbCommand = dispenseCommandRepository.findByCommandId(commandId).orElseThrow();
        assertThat(dbCommand.getStatus()).isEqualTo("PUBLISHED");

        // 2. Simulate ESP32 sending COMMAND_ACCEPTED on vending/VM001/events
        UUID acceptedEventId = UUID.randomUUID();
        DeviceEventMessage acceptedEvent = DeviceEventMessage.accepted(
                acceptedEventId, commandId, "VM001", orderId, 2, Instant.now()
        );
        mqttDeviceAdapter.messageArrived("vending/VM001/events",
                new MqttMessage(objectMapper.writeValueAsString(acceptedEvent).getBytes(StandardCharsets.UTF_8)));

        // Verify DB DispenseCommand transitioned to ACKNOWLEDGED
        dbCommand = dispenseCommandRepository.findByCommandId(commandId).orElseThrow();
        assertThat(dbCommand.getStatus()).isEqualTo("ACKNOWLEDGED");

        // 3. Simulate ESP32 finishing motor cycle and publishing DISPENSE_RESULT (SUCCESS)
        reset(mockMqttClient); // Reset to verify subsequent ACK publication
        when(mockMqttClient.isConnected()).thenReturn(true);

        UUID resultEventId = UUID.randomUUID();
        DeviceEventMessage resultEvent = DeviceEventMessage.result(
                resultEventId, commandId, "VM001", orderId, 2,
                "SUCCESS", "MOTOR_CYCLE_ONLY", null, null, Instant.now()
        );
        mqttDeviceAdapter.messageArrived("vending/VM001/events",
                new MqttMessage(objectMapper.writeValueAsString(resultEvent).getBytes(StandardCharsets.UTF_8)));

        // Verify Order COMPLETED
        Order completedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completedOrder.isReservationHeld()).isFalse();

        // Verify Product stock deducted (from 10 to 9) and reserved_quantity returned to 0
        Product updatedProduct = productRepository.findById(productSlot2.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(9);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);

        // Verify Backend sent EVENT_ACK on vending/VM001/events/ack
        ArgumentCaptor<String> ackTopicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> ackMessageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mockMqttClient).publish(ackTopicCaptor.capture(), ackMessageCaptor.capture());

        assertThat(ackTopicCaptor.getValue()).isEqualTo("vending/VM001/events/ack");
        String ackPayload = new String(ackMessageCaptor.getValue().getPayload(), StandardCharsets.UTF_8);
        assertThat(ackPayload).contains(resultEventId.toString());
        assertThat(ackPayload).contains("\"status\":\"PROCESSED\"");

        // 4. Test Idempotency: Redelivering duplicate DISPENSE_RESULT event
        reset(mockMqttClient);
        when(mockMqttClient.isConnected()).thenReturn(true);

        mqttDeviceAdapter.messageArrived("vending/VM001/events",
                new MqttMessage(objectMapper.writeValueAsString(resultEvent).getBytes(StandardCharsets.UTF_8)));

        // Stock must still be 9 (not deducted twice!)
        updatedProduct = productRepository.findById(productSlot2.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(9);

        // Backend must re-send EVENT_ACK for idempotent recovery
        verify(mockMqttClient).publish(eq("vending/VM001/events/ack"), any(MqttMessage.class));
    }

    @Test
    @DisplayName("MQTT Failure Handling: DISPENSE_RESULT with FAILURE releases reservation without stock deduction")
    void testMqttDispenseFailureHandling() throws Exception {
        Order order = createAndPayOrder(productSlot1);
        UUID orderId = order.getId();

        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(orderId);
        UUID commandId = response.getCommandId();

        reset(mockMqttClient);
        when(mockMqttClient.isConnected()).thenReturn(true);

        // Simulate FAILURE event from ESP32
        UUID resultEventId = UUID.randomUUID();
        DeviceEventMessage failureEvent = DeviceEventMessage.result(
                resultEventId, commandId, "VM001", orderId, 1,
                "FAILURE", "MOTOR_CYCLE_ONLY", "MOTOR_TIMEOUT", "Motor stall detected", Instant.now()
        );
        mqttDeviceAdapter.messageArrived("vending/VM001/events",
                new MqttMessage(objectMapper.writeValueAsString(failureEvent).getBytes(StandardCharsets.UTF_8)));

        Order failedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.DISPENSE_FAILED);
        assertThat(failedOrder.isReservationHeld()).isFalse();

        // Stock quantity preserved at 10, reservation cleared
        Product product = productRepository.findById(productSlot1.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(product.getReservedQuantity()).isEqualTo(0);

        // EVENT_ACK still sent
        verify(mockMqttClient).publish(eq("vending/VM001/events/ack"), any(MqttMessage.class));
    }

    @Test
    @DisplayName("MQTT Uncertain Handling: DISPENSE_RESULT with UNCERTAIN safely moves order to NEEDS_RECONCILIATION")
    void testMqttDispenseUncertainHandling() throws Exception {
        Order order = createAndPayOrder(productSlot3);
        UUID orderId = order.getId();

        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(orderId);
        UUID commandId = response.getCommandId();

        reset(mockMqttClient);
        when(mockMqttClient.isConnected()).thenReturn(true);

        // Simulate UNCERTAIN outcome (e.g. reboot during motor run)
        UUID resultEventId = UUID.randomUUID();
        DeviceEventMessage uncertainEvent = DeviceEventMessage.result(
                resultEventId, commandId, "VM001", orderId, 3,
                "UNCERTAIN", "MOTOR_CYCLE_ONLY", "REBOOT_DURING_EXECUTION", "Device rebooted during run", Instant.now()
        );
        mqttDeviceAdapter.messageArrived("vending/VM001/events",
                new MqttMessage(objectMapper.writeValueAsString(uncertainEvent).getBytes(StandardCharsets.UTF_8)));

        Order uncertainOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(uncertainOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        // Reservation and stock are preserved for manual admin reconciliation
        assertThat(uncertainOrder.isReservationHeld()).isTrue();

        Product product = productRepository.findById(productSlot3.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(product.getReservedQuantity()).isEqualTo(1);

        verify(mockMqttClient).publish(eq("vending/VM001/events/ack"), any(MqttMessage.class));
    }

    @Test
    @DisplayName("MQTT Offline Machine: If device is offline, triggerDispensing safely transitions to NEEDS_RECONCILIATION without sending command")
    void testMqttDeviceOfflineSafeHold() throws Exception {
        // Create an order on a machine without fresh heartbeat
        Machine offlineMachine = machineRepository.save(new Machine(
                UUID.randomUUID(), "VM_OFFLINE", "Offline Machine", MachineStatus.ACTIVE, null
        ));
        Product offlineProduct = productRepository.save(new Product(
                UUID.randomUUID(), offlineMachine, "Offline Drink", "Slot 1",
                "https://example.com/drink.jpg", 10000, 1, 10, 0, true
        ));

        OrderResponseDto created = orderService.createOrder(new CreateOrderRequestDto("VM_OFFLINE", offlineProduct.getId(), 1));
        Order order = orderRepository.findById(created.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        order = orderRepository.save(order);

        reset(mockMqttClient);
        when(mockMqttClient.isConnected()).thenReturn(true);

        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(order.getId());
        assertThat(response.getStatus()).isEqualTo("NEEDS_RECONCILIATION");

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        // No MQTT command should have been dispatched
        verify(mockMqttClient, never()).publish(anyString(), any(MqttMessage.class));
    }

    @Test
    @DisplayName("Slot Mapping: Slots 1, 2, and 3 are mapped accurately into DispenseCommandMessage")
    void testSlotMappingAccuracy() throws Exception {
        Product[] products = {productSlot1, productSlot2, productSlot3};
        int[] expectedSlots = {1, 2, 3};

        for (int i = 0; i < products.length; i++) {
            reset(mockMqttClient);
            when(mockMqttClient.isConnected()).thenReturn(true);

            Order order = createAndPayOrder(products[i]);
            dispenseService.triggerDispensing(order.getId());

            ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mockMqttClient).publish(eq("vending/VM001/commands"), captor.capture());

            String payload = new String(captor.getValue().getPayload(), StandardCharsets.UTF_8);
            assertThat(payload).contains("\"slotNumber\":" + expectedSlots[i]);
        }
    }
}
