package com.vendingmachine.dispense;

import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.adapter.simulated.SimulatedDeviceAdapter;
import com.vendingmachine.dispense.adapter.simulated.SimulationScenario;
import com.vendingmachine.dispense.entity.DispenseCommand;
import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.dispense.service.DispenseRecoveryService;
import com.vendingmachine.dispense.service.DispenseService;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.repository.MachineRepository;
import com.vendingmachine.order.dto.CreateOrderRequestDto;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderItemRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.dispense.recovery.enabled=true")
@ActiveProfiles("test")
public class DispenseRecoveryIntegrationTest {

    @Autowired
    private DispenseRecoveryService dispenseRecoveryService;

    @Autowired
    private DispenseService dispenseService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

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
        simulatedDeviceAdapter.setAsyncMode(false);
        simulatedDeviceAdapter.setScenario(SimulationScenario.SUCCESS);

        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();

        testMachine = new Machine(
                UUID.randomUUID(),
                "VM_RECOVERY",
                "Máy kiểm thử Phục hồi Đơn mồ côi",
                MachineStatus.ACTIVE,
                null
        );
        testMachine = machineRepository.save(testMachine);

        testProduct = new Product(
                UUID.randomUUID(),
                testMachine,
                "Aqua 500ml",
                "Đồ uống",
                "https://example.com/aqua.jpg",
                8000,
                1,
                10,
                0,
                true
        );
        testProduct = productRepository.save(testProduct);

        // Ensure default parameters in recovery service
        ReflectionTestUtils.setField(dispenseRecoveryService, "minAgeSeconds", 30);
        ReflectionTestUtils.setField(dispenseRecoveryService, "maxAgeMinutes", 15);
    }

    private Order createOrphanedPaidOrder(int paidAgeSeconds) {
        CreateOrderRequestDto req = new CreateOrderRequestDto("VM_RECOVERY", testProduct.getId(), 1);
        OrderResponseDto dto = orderService.createOrder(req);

        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now().minusSeconds(paidAgeSeconds));
        order.setUpdatedAt(Instant.now().minusSeconds(paidAgeSeconds));
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("1. Đơn PAID không có lệnh nhả hàng sau thời gian chờ (minAge) được phát hiện và phục hồi thành công")
    void test1_OrphanedPaidOrder_AfterMinAge_IsDetectedAndRecovered() {
        // Tạo đơn PAID 45 giây trước (lớn hơn minAgeSeconds = 30s)
        Order order = createOrphanedPaidOrder(45);
        UUID orderId = order.getId();

        Product prodBefore = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodBefore.getStockQuantity()).isEqualTo(10);
        assertThat(prodBefore.getReservedQuantity()).isEqualTo(1);

        // Chạy recovery poller
        int recovered = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered).isEqualTo(1);

        // Kiểm tra Order đã hoàn tất COMPLETED
        Order orderAfter = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderAfter.getCompletedAt()).isNotNull();

        // Kiểm tra lệnh nhả hàng được tạo duy nhất và COMPLETED
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("COMPLETED");

        // Kiểm tra tồn kho: trừ stock_quantity và giải phóng reserved_quantity đúng 1 lần
        Product prodAfter = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfter.getStockQuantity()).isEqualTo(9);
        assertThat(prodAfter.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("2. Đơn PAID mới tạo chưa đủ thời gian chờ (dưới minAge) không bị xử lý")
    void test2_FreshPaidOrder_WithinMinAge_IsNotRecovered() {
        // Tạo đơn PAID mới 5 giây trước (nhỏ hơn minAgeSeconds = 30s)
        Order order = createOrphanedPaidOrder(5);
        UUID orderId = order.getId();

        int recovered = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered).isEqualTo(0);

        Order orderAfter = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.PAID);

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands).isEmpty();

        Product prodAfter = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfter.getStockQuantity()).isEqualTo(10);
        assertThat(prodAfter.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("3. Đơn PAID quá cũ vượt quá maxRecoveryAge (15 phút) không bị auto-dispense (bảo vệ đơn 4648baae)")
    void test3_OldPaidOrderExceedingMaxAge_IsNotRecovered() {
        // Tạo đơn PAID từ 20 phút trước (> 15m)
        Order order = createOrphanedPaidOrder(20 * 60);
        UUID orderId = order.getId();

        int recovered = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered).isEqualTo(0);

        Order orderAfter = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.PAID);

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands).isEmpty();

        Product prodAfter = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfter.getStockQuantity()).isEqualTo(10);
        assertThat(prodAfter.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("4. Đơn đã có lệnh nhả hàng không bị tạo lệnh thứ hai")
    void test4_PaidOrderWithExistingDispenseCommand_DoesNotCreateSecondCommand() {
        Order order = createOrphanedPaidOrder(45);
        UUID orderId = order.getId();

        // Tạo thủ công 1 DispenseCommand đang PENDING cho đơn này
        DispenseCommand cmd = new DispenseCommand();
        cmd.setCommandId(UUID.randomUUID());
        cmd.setOrder(order);
        cmd.setMachine(testMachine);
        cmd.setSlotNumber(1);
        cmd.setStatus("PENDING");
        dispenseCommandRepository.save(cmd);

        int recovered = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered).isEqualTo(0);

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands).hasSize(1);
    }

    @Test
    @DisplayName("5. Nhiều lượt poll hoặc xử lý đồng thời không tạo lệnh nhả trùng và không trừ tồn kho hai lần")
    void test5_ConcurrentRecoveryPolls_DoesNotCreateDuplicateCommandsOrDoubleDeductStock() throws InterruptedException {
        Order order = createOrphanedPaidOrder(45);
        UUID orderId = order.getId();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalRecoveredCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int res = dispenseRecoveryService.recoverOrphanedPaidOrders();
                    totalRecoveredCount.addAndGet(res);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Kiểm tra chỉ duy nhất 1 lệnh nhả hàng được tạo
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("COMPLETED");

        // Kiểm tra tồn kho chỉ bị trừ đúng 1 lần
        Product prodAfter = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfter.getStockQuantity()).isEqualTo(9);
        assertThat(prodAfter.getReservedQuantity()).isEqualTo(0);

        Order orderAfter = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("6. Đơn COMPLETED, EXPIRED, DISPENSE_FAILED không bị phục hồi sai")
    void test6_OtherOrderStatuses_NotRecovered() {
        CreateOrderRequestDto req1 = new CreateOrderRequestDto("VM_RECOVERY", testProduct.getId(), 1);
        Order o1 = orderRepository.findById(orderService.createOrder(req1).getOrderId()).orElseThrow();
        o1.setStatus(OrderStatus.COMPLETED);
        o1.setPaidAt(Instant.now().minusSeconds(60));
        orderRepository.save(o1);

        CreateOrderRequestDto req2 = new CreateOrderRequestDto("VM_RECOVERY", testProduct.getId(), 1);
        Order o2 = orderRepository.findById(orderService.createOrder(req2).getOrderId()).orElseThrow();
        o2.setStatus(OrderStatus.EXPIRED);
        o2.setPaidAt(Instant.now().minusSeconds(60));
        orderRepository.save(o2);

        CreateOrderRequestDto req3 = new CreateOrderRequestDto("VM_RECOVERY", testProduct.getId(), 1);
        Order o3 = orderRepository.findById(orderService.createOrder(req3).getOrderId()).orElseThrow();
        o3.setStatus(OrderStatus.DISPENSE_FAILED);
        o3.setPaidAt(Instant.now().minusSeconds(60));
        orderRepository.save(o3);

        int recovered = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered).isEqualTo(0);

        assertThat(dispenseCommandRepository.findByOrderId(o1.getId())).isEmpty();
        assertThat(dispenseCommandRepository.findByOrderId(o2.getId())).isEmpty();
        assertThat(dispenseCommandRepository.findByOrderId(o3.getId())).isEmpty();
    }

    @Test
    @DisplayName("7. Đơn có kết quả nhả hàng UNCERTAIN không bị tự động nhả lại; tồn kho được bảo toàn cho đối soát")
    void test7_UncertainDispenseOutcome_NeverAutoRetried() {
        simulatedDeviceAdapter.setScenario(SimulationScenario.UNCERTAIN);

        Order order = createOrphanedPaidOrder(45);
        UUID orderId = order.getId();

        // Lượt recovery 1: máy phản hồi UNCERTAIN
        int recovered1 = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered1).isEqualTo(1);

        Order orderAfter1 = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter1.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        List<DispenseCommand> commands1 = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands1).hasSize(1);
        assertThat(commands1.get(0).getStatus()).isEqualTo("UNCERTAIN");

        // Tồn kho và reservation phải được giữ nguyên để đối soát
        Product prodAfter1 = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfter1.getStockQuantity()).isEqualTo(10);
        assertThat(prodAfter1.getReservedQuantity()).isEqualTo(1);

        // Lượt recovery 2: Poller quét lại
        int recovered2 = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered2).isEqualTo(0);

        // Lệnh và trạng thái không bị thay đổi, không tự động nhả lại
        List<DispenseCommand> commands2 = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands2).hasSize(1);
        Product prodAfter2 = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfter2.getStockQuantity()).isEqualTo(10);
        assertThat(prodAfter2.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("8. Thiết bị OFFLINE khi phục hồi: chuyển an toàn sang NEEDS_RECONCILIATION và không tự động nhả lại")
    void test8_DeviceOfflineDuringRecovery_TransitionsToNeedsReconciliation_AndNotRetried() {
        simulatedDeviceAdapter.setScenario(SimulationScenario.OFFLINE);

        Order order = createOrphanedPaidOrder(45);
        UUID orderId = order.getId();

        // Lượt recovery 1
        int recovered1 = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered1).isEqualTo(1);

        Order orderAfter1 = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter1.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        List<DispenseCommand> commands1 = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands1).hasSize(1);
        assertThat(commands1.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(commands1.get(0).getFailureCode()).isEqualTo("DEVICE_OFFLINE");

        // Lượt recovery 2
        int recovered2 = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered2).isEqualTo(0);

        List<DispenseCommand> commands2 = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands2).hasSize(1);
    }
}
