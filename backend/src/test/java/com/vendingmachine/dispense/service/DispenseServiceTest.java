package com.vendingmachine.dispense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.device.entity.DeviceEvent;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.adapter.DeviceAdapter;
import com.vendingmachine.dispense.dto.DeviceEventMessage;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
import com.vendingmachine.dispense.entity.DispenseCommand;
import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.repository.MachineRepository;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispenseServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private DispenseCommandRepository dispenseCommandRepository;

    @Mock
    private DeviceEventRepository deviceEventRepository;

    @Mock
    private DeviceAdapter deviceAdapter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DispenseServiceImpl dispenseService;

    private Machine sampleMachine;
    private Product sampleProduct;
    private Order sampleOrder;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispenseService, "commandTimeoutSeconds", 45);
        ReflectionTestUtils.setField(dispenseService, "motorRunMs", 800);

        sampleMachine = new Machine(UUID.randomUUID(), "VM001", "Máy thử nghiệm", MachineStatus.ACTIVE, null);
        sampleProduct = new Product(UUID.randomUUID(), sampleMachine, "Coca Cola", "Nước ngọt", "https://img.com",
                10000, 1, 10, 1, true);

        orderId = UUID.randomUUID();
        sampleOrder = new Order();
        sampleOrder.setId(orderId);
        sampleOrder.setPublicOrderCode("VM001-20261019-1234");
        sampleOrder.setMachine(sampleMachine);
        sampleOrder.setProduct(sampleProduct);
        sampleOrder.setQuantity(1);
        sampleOrder.setUnitPriceVnd(10000);
        sampleOrder.setTotalAmountVnd(10000);
        sampleOrder.setTransferReference("ORDER123");
        sampleOrder.setStatus(OrderStatus.PAID);
        sampleOrder.setPaymentExpiresAt(Instant.now().plusSeconds(900));
        sampleOrder.setPaidAt(Instant.now());
    }

    @Test
    @DisplayName("Từ chối kích hoạt xuất hàng nếu đơn hàng chưa ở trạng thái PAID")
    void triggerDispensing_rejectIfNotPaid() {
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> dispenseService.triggerDispensing(orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ đơn hàng đã thanh toán mới được phép xuất hàng");

        verify(deviceAdapter, never()).sendDispenseCommand(any());
    }

    @Test
    @DisplayName("Kích hoạt xuất hàng thành công khi đơn PAID và thiết bị ONLINE")
    void triggerDispensing_successWhenPaidAndOnline() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(sampleOrder));
        when(dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Collections.emptyList());
        when(deviceAdapter.isDeviceOnline("VM001")).thenReturn(true);
        when(dispenseCommandRepository.save(any(DispenseCommand.class))).thenAnswer(i -> i.getArgument(0));

        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("DISPENSING");
        assertThat(response.getCommandId()).isNotNull();
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.DISPENSING);
        assertThat(sampleOrder.getDispensingStartedAt()).isNotNull();

        verify(deviceAdapter).sendDispenseCommand(any());
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("Khi thiết bị OFFLINE: Đơn hàng chuyển sang NEEDS_RECONCILIATION an toàn, không phát lệnh")
    void triggerDispensing_offlineDeviceHandling() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(sampleOrder));
        when(dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Collections.emptyList());
        when(deviceAdapter.isDeviceOnline("VM001")).thenReturn(false);
        when(dispenseCommandRepository.save(any(DispenseCommand.class))).thenAnswer(i -> i.getArgument(0));

        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(orderId);

        assertThat(response.getStatus()).isEqualTo("NEEDS_RECONCILIATION");
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        verify(deviceAdapter, never()).sendDispenseCommand(any());
    }

    @Test
    @DisplayName("Nhận COMMAND_ACCEPTED: Cập nhật lệnh sang ACKNOWLEDGED")
    void handleDeviceEvent_commandAccepted() {
        UUID cmdId = UUID.randomUUID();
        DispenseCommand command = new DispenseCommand();
        command.setCommandId(cmdId);
        command.setOrder(sampleOrder);
        command.setStatus("PUBLISHED");

        DeviceEventMessage event = DeviceEventMessage.accepted(
                UUID.randomUUID(), cmdId, "VM001", orderId, 1, Instant.now()
        );

        when(deviceEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        when(dispenseCommandRepository.findByCommandId(cmdId)).thenReturn(Optional.of(command));

        dispenseService.handleDeviceEvent(event);

        assertThat(command.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(command.getAcknowledgedAt()).isNotNull();
        verify(dispenseCommandRepository).save(command);
        verify(deviceAdapter).sendEventAck(any());
    }

    @Test
    @DisplayName("Nhận DISPENSE_RESULT(SUCCESS): Đơn hàng COMPLETED, trừ stock_quantity và giải phóng reserved_quantity đúng 1 lần")
    void handleDeviceEvent_dispenseResultSuccess() {
        sampleOrder.setStatus(OrderStatus.DISPENSING);
        UUID cmdId = UUID.randomUUID();
        DispenseCommand command = new DispenseCommand();
        command.setCommandId(cmdId);
        command.setOrder(sampleOrder);
        command.setStatus("ACKNOWLEDGED");

        DeviceEventMessage event = DeviceEventMessage.result(
                UUID.randomUUID(), cmdId, "VM001", orderId, 1,
                "SUCCESS", "MOTOR_CYCLE_ONLY", null, null, Instant.now()
        );

        when(deviceEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        when(dispenseCommandRepository.findByCommandId(cmdId)).thenReturn(Optional.of(command));
        when(productRepository.findByIdForUpdate(sampleProduct.getId())).thenReturn(Optional.of(sampleProduct));

        dispenseService.handleDeviceEvent(event);

        // Kiểm tra order -> COMPLETED
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(sampleOrder.getCompletedAt()).isNotNull();

        // Kiểm tra command -> COMPLETED
        assertThat(command.getStatus()).isEqualTo("COMPLETED");

        // Kiểm tra tồn kho: stockQuantity: 10 -> 9, reservedQuantity: 1 -> 0
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(9);
        assertThat(sampleProduct.getReservedQuantity()).isEqualTo(0);

        verify(orderRepository).save(sampleOrder);
        verify(productRepository).save(sampleProduct);
        verify(deviceAdapter).sendEventAck(any());
    }

    @Test
    @DisplayName("Idempotent check: Nhận lại DISPENSE_RESULT(SUCCESS) cho đơn đã COMPLETED thì không trừ kho lần 2")
    void handleDeviceEvent_duplicateSuccessDoesNotDeductTwice() {
        sampleOrder.setStatus(OrderStatus.COMPLETED);
        sampleProduct.setStockQuantity(9);
        sampleProduct.setReservedQuantity(0);

        UUID cmdId = UUID.randomUUID();
        DispenseCommand command = new DispenseCommand();
        command.setCommandId(cmdId);
        command.setOrder(sampleOrder);
        command.setStatus("COMPLETED");

        DeviceEventMessage event = DeviceEventMessage.result(
                UUID.randomUUID(), cmdId, "VM001", orderId, 1,
                "SUCCESS", "MOTOR_CYCLE_ONLY", null, null, Instant.now()
        );

        when(deviceEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        when(dispenseCommandRepository.findByCommandId(cmdId)).thenReturn(Optional.of(command));

        dispenseService.handleDeviceEvent(event);

        // Tồn kho không bị trừ thêm lần nữa
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(9);
        verify(productRepository, never()).save(any());
        verify(deviceAdapter).sendEventAck(any());
    }

    @Test
    @DisplayName("Nhận DISPENSE_RESULT(FAILURE): Đơn hàng DISPENSE_FAILED, KHÔNG trừ stock_quantity, giải phóng reserved_quantity")
    void handleDeviceEvent_dispenseResultFailure() {
        sampleOrder.setStatus(OrderStatus.DISPENSING);
        UUID cmdId = UUID.randomUUID();
        DispenseCommand command = new DispenseCommand();
        command.setCommandId(cmdId);
        command.setOrder(sampleOrder);
        command.setStatus("ACKNOWLEDGED");

        DeviceEventMessage event = DeviceEventMessage.result(
                UUID.randomUUID(), cmdId, "VM001", orderId, 1,
                "FAILURE", "MOTOR_CYCLE_ONLY", "MOTOR_TIMEOUT", "Motor stall detected", Instant.now()
        );

        when(deviceEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        when(dispenseCommandRepository.findByCommandId(cmdId)).thenReturn(Optional.of(command));
        when(productRepository.findByIdForUpdate(sampleProduct.getId())).thenReturn(Optional.of(sampleProduct));

        dispenseService.handleDeviceEvent(event);

        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.DISPENSE_FAILED);
        assertThat(command.getStatus()).isEqualTo("FAILED");
        assertThat(command.getFailureCode()).isEqualTo("MOTOR_TIMEOUT");

        // QUY TẮC QUAN TRỌNG: stockQuantity KHÔNG ĐƯỢC PHÉP TRỪ KHI THẤT BẠI!
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(10);
        // reservedQuantity được giải phóng
        assertThat(sampleProduct.getReservedQuantity()).isEqualTo(0);

        verify(orderRepository).save(sampleOrder);
        verify(productRepository).save(sampleProduct);
        verify(deviceAdapter).sendEventAck(any());
    }

    @Test
    @DisplayName("Nhận DISPENSE_RESULT(UNCERTAIN): Đơn hàng chuyển sang NEEDS_RECONCILIATION, tồn kho và reservation đều giữ nguyên")
    void handleDeviceEvent_dispenseResultUncertain() {
        sampleOrder.setStatus(OrderStatus.DISPENSING);
        UUID cmdId = UUID.randomUUID();
        DispenseCommand command = new DispenseCommand();
        command.setCommandId(cmdId);
        command.setOrder(sampleOrder);
        command.setStatus("ACKNOWLEDGED");

        DeviceEventMessage event = DeviceEventMessage.result(
                UUID.randomUUID(), cmdId, "VM001", orderId, 1,
                "UNCERTAIN", "MOTOR_CYCLE_ONLY", "REBOOT_DURING_EXECUTION", "Reboot while executing", Instant.now()
        );

        when(deviceEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        when(dispenseCommandRepository.findByCommandId(cmdId)).thenReturn(Optional.of(command));

        dispenseService.handleDeviceEvent(event);

        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        assertThat(command.getStatus()).isEqualTo("UNCERTAIN");
        assertThat(command.getFailureCode()).isEqualTo("REBOOT_DURING_EXECUTION");

        // Cả stockQuantity và reservedQuantity giữ nguyên phục vụ đối soát
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(10);
        assertThat(sampleProduct.getReservedQuantity()).isEqualTo(1);

        verify(productRepository, never()).save(any());
        verify(orderRepository).save(sampleOrder);
        verify(deviceAdapter).sendEventAck(any());
    }

    @Test
    @DisplayName("Lệnh bị timeout: Chuyển sang TIMEOUT và đơn hàng chuyển sang NEEDS_RECONCILIATION")
    void checkAndHandleTimeouts_handlesExpiredCommands() {
        sampleOrder.setStatus(OrderStatus.DISPENSING);
        UUID cmdId = UUID.randomUUID();
        DispenseCommand command = new DispenseCommand();
        command.setCommandId(cmdId);
        command.setOrder(sampleOrder);
        command.setStatus("PUBLISHED");
        command.setCreatedAt(Instant.now().minusSeconds(60));

        when(dispenseCommandRepository.findTimedOutCommands(any(), any()))
                .thenReturn(List.of(command));

        int handled = dispenseService.checkAndHandleTimeouts();

        assertThat(handled).isEqualTo(1);
        assertThat(command.getStatus()).isEqualTo("TIMEOUT");
        assertThat(command.getFailureCode()).isEqualTo("TIMEOUT");
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        verify(dispenseCommandRepository).save(command);
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("Duplicate eventId: Không áp dụng thay đổi DB nhưng luôn gửi lại EVENT_ACK")
    void handleDeviceEvent_duplicateEventIdAlwaysResendsAck() {
        UUID eventId = UUID.randomUUID();
        DeviceEventMessage event = DeviceEventMessage.accepted(
                eventId, UUID.randomUUID(), "VM001", orderId, 1, Instant.now()
        );

        when(deviceEventRepository.existsByEventId(eventId)).thenReturn(true);

        dispenseService.handleDeviceEvent(event);

        // Không ghi lại deviceEvent, không đổi command
        verify(deviceEventRepository, never()).save(any());
        verify(dispenseCommandRepository, never()).save(any());
        // Bắt buộc gửi lại EVENT_ACK
        verify(deviceAdapter).sendEventAck(any());
    }
}
