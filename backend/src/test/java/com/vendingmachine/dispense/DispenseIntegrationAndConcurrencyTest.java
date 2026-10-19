package com.vendingmachine.dispense;

import com.vendingmachine.device.entity.DeviceEvent;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.adapter.simulated.SimulatedDeviceAdapter;
import com.vendingmachine.dispense.adapter.simulated.SimulationScenario;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class DispenseIntegrationAndConcurrencyTest {

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
    private SimulatedDeviceAdapter simulatedDeviceAdapter;

    private Machine testMachine;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        simulatedDeviceAdapter.resetState();
        simulatedDeviceAdapter.setAsyncMode(false); // Run synchronously for deterministic unit/integration testing
        simulatedDeviceAdapter.setScenario(SimulationScenario.SUCCESS);

        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();

        testMachine = new Machine(
                UUID.randomUUID(),
                "VM_DISPENSE",
                "Máy kiểm thử Xuất hàng Tự động",
                MachineStatus.ACTIVE,
                null
        );
        testMachine = machineRepository.save(testMachine);

        testProduct = new Product(
                UUID.randomUUID(),
                testMachine,
                "Trà xanh Không độ",
                "Đồ uống giải khát",
                "https://example.com/tra-xanh.jpg",
                12000,
                1,
                10, // stock_quantity = 10
                0,  // reserved_quantity = 0
                true
        );
        testProduct = productRepository.save(testProduct);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();
    }

    private Order createAndPayOrder() {
        OrderResponseDto created = orderService.createOrder(new CreateOrderRequestDto("VM_DISPENSE", testProduct.getId(), 1));
        Order order = orderRepository.findById(created.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("Luồng chuẩn SUCCESS: Đơn hàng hoàn tất COMPLETED, trừ stock đúng 1 lần và giải phóng giữ chỗ")
    void happyPath_dispensesSuccessfully() {
        Order order = createAndPayOrder();
        simulatedDeviceAdapter.setScenario(SimulationScenario.SUCCESS);

        DispenseTriggerResponseDto response = dispenseService.triggerDispensing(order.getId());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("DISPENSING");

        // Kiểm tra Order đã hoàn tất
        Order finishedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finishedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(finishedOrder.getCompletedAt()).isNotNull();

        // Kiểm tra tồn kho vật lý: stock_quantity 10 -> 9, reserved_quantity 1 -> 0
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(9);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);
        assertThat(updatedProduct.getAvailableStock()).isEqualTo(9);

        // Kiểm tra DispenseCommand và DeviceEvent trong DB
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(order.getId());
        assertThat(commands).hasSize(1);
        DispenseCommand cmd = commands.get(0);
        assertThat(cmd.getStatus()).isEqualTo("COMPLETED");
        assertThat(cmd.getSlotNumber()).isEqualTo(1);
        assertThat(cmd.getFinishedAt()).isNotNull();

        List<DeviceEvent> events = deviceEventRepository.findByCommandIdOrderByCreatedAtAsc(cmd.getCommandId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("COMMAND_ACCEPTED");
        assertThat(events.get(1).getEventType()).isEqualTo("DISPENSE_RESULT");
    }

    @Test
    @DisplayName("Kịch bản THẤT BẠI (FAILURE): Đơn hàng DISPENSE_FAILED, KHÔNG trừ stock, giải phóng reserved")
    void failureOutcome_preservesStock() {
        Order order = createAndPayOrder();
        simulatedDeviceAdapter.setScenario(SimulationScenario.FAILURE);

        dispenseService.triggerDispensing(order.getId());

        Order finishedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finishedOrder.getStatus()).isEqualTo(OrderStatus.DISPENSE_FAILED);

        // QUY TẮC CỐT LÕI: Tồn kho vật lý tuyệt đối KHÔNG bị trừ khi nhả hàng thất bại!
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(10);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);
        assertThat(updatedProduct.getAvailableStock()).isEqualTo(10);

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(order.getId());
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(commands.get(0).getFailureCode()).isEqualTo("MOTOR_TIMEOUT");
    }

    @Test
    @DisplayName("Kịch bản BẤT ĐỊNH (UNCERTAIN - Reboot giữa chừng): Đơn hàng chuyển NEEDS_RECONCILIATION, giữ nguyên kho")
    void uncertainOutcome_preservesStateForReconciliation() {
        Order order = createAndPayOrder();
        simulatedDeviceAdapter.setScenario(SimulationScenario.UNCERTAIN);

        dispenseService.triggerDispensing(order.getId());

        Order finishedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finishedOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        // Bảo lưu tồn kho và giữ chỗ để đối soát thủ công an toàn
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(10);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(1);

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(order.getId());
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("UNCERTAIN");
        assertThat(commands.get(0).getFailureCode()).isEqualTo("REBOOT_DURING_EXECUTION");
    }

    @Test
    @DisplayName("Kịch bản THIẾT BỊ OFFLINE: Không phát lệnh ra máy, chuyển ngay sang NEEDS_RECONCILIATION")
    void offlineDevice_transitionsToReconciliation() {
        Order order = createAndPayOrder();
        simulatedDeviceAdapter.setScenario(SimulationScenario.OFFLINE);

        DispenseTriggerResponseDto res = dispenseService.triggerDispensing(order.getId());

        assertThat(res.getStatus()).isEqualTo("NEEDS_RECONCILIATION");
        Order finishedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finishedOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        // Tồn kho không bị trừ
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(10);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(1);

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(order.getId());
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(commands.get(0).getFailureCode()).isEqualTo("DEVICE_OFFLINE");
    }

    @Test
    @DisplayName("Kịch bản TIMEOUT: Scheduler phát hiện lệnh quá hạn chuyển sang NEEDS_RECONCILIATION")
    void timeout_transitionsStaleCommand() {
        Order order = createAndPayOrder();
        simulatedDeviceAdapter.setScenario(SimulationScenario.TIMEOUT);

        dispenseService.triggerDispensing(order.getId());

        // Lệnh đang ở trạng thái ACKNOWLEDGED, đơn hàng ở DISPENSING
        Order inFlightOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(inFlightOrder.getStatus()).isEqualTo(OrderStatus.DISPENSING);

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(order.getId());
        assertThat(commands).hasSize(1);
        DispenseCommand cmd = commands.get(0);
        assertThat(cmd.getStatus()).isEqualTo("ACKNOWLEDGED");

        // Giả lập lệnh được phát từ 60 giây trước để kích hoạt timeout logic
        cmd.setPublishedAt(Instant.now().minusSeconds(60));
        dispenseCommandRepository.save(cmd);

        // Chạy job quét timeout
        int handled = dispenseService.checkAndHandleTimeouts();
        assertThat(handled).isEqualTo(1);

        Order reconciledOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reconciledOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        DispenseCommand timedOutCmd = dispenseCommandRepository.findById(cmd.getId()).orElseThrow();
        assertThat(timedOutCmd.getStatus()).isEqualTo("TIMEOUT");
        assertThat(timedOutCmd.getFailureCode()).isEqualTo("TIMEOUT");

        // Tồn kho vật lý không bị trừ khi timeout
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Kiểm thử Đồng thời (Concurrency): 10 threads cùng gọi triggerDispensing cho 1 đơn hàng -> Chỉ 1 lệnh thành công")
    void concurrencyRace_multipleTriggersSameOrder_onlyDispensesOnce() throws InterruptedException {
        Order order = createAndPayOrder();
        simulatedDeviceAdapter.setScenario(SimulationScenario.SUCCESS);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    DispenseTriggerResponseDto res = dispenseService.triggerDispensing(order.getId());
                    if (res != null) {
                        successes.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        // Kiểm tra: Đơn hàng hoàn tất COMPLETED
        Order finishedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finishedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Kiểm tra database: Đúng 1 DispenseCommand được tạo
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(order.getId());
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("COMPLETED");

        // Kiểm tra tồn kho vật lý: stock chỉ bị trừ đúng 1 lần (10 -> 9), tuyệt đối không trừ 10 lần!
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(9);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);
    }
}
