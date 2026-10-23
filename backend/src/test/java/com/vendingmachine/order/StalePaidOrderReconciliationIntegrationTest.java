package com.vendingmachine.order;

import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.adapter.simulated.SimulatedDeviceAdapter;
import com.vendingmachine.dispense.adapter.simulated.SimulationScenario;
import com.vendingmachine.dispense.entity.DispenseCommand;
import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.dispense.service.DispenseRecoveryService;
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
import com.vendingmachine.order.service.StalePaidOrderReconciliationService;
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

@SpringBootTest(properties = {
        "app.dispense.recovery.enabled=true",
        "app.dispense.recovery.tagger-enabled=true"
})
@ActiveProfiles("test")
public class StalePaidOrderReconciliationIntegrationTest {

    @Autowired
    private StalePaidOrderReconciliationService reconciliationService;

    @Autowired
    private DispenseRecoveryService dispenseRecoveryService;

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
                "VM_TAGGER",
                "Máy kiểm thử Stale Order Reconciliation",
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

        ReflectionTestUtils.setField(reconciliationService, "maxAgeMinutes", 15);
    }

    private Order createPaidOrderWithAge(int paidAgeMinutes) {
        CreateOrderRequestDto req = new CreateOrderRequestDto("VM_TAGGER", testProduct.getId(), 1);
        OrderResponseDto dto = orderService.createOrder(req);

        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now().minus(paidAgeMinutes, ChronoUnit.MINUTES));
        order.setUpdatedAt(Instant.now().minus(paidAgeMinutes, ChronoUnit.MINUTES));
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("1. Đơn PAID quá 15 phút, chưa có command -> chuyển sang NEEDS_RECONCILIATION")
    void test1_StalePaidOrder_Exceeding15Min_TaggedForReconciliation() {
        // Đơn đã thanh toán từ 20 phút trước (> 15m), chưa có dispense command
        Order order = createPaidOrderWithAge(20);
        UUID orderId = order.getId();

        int tagged = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(tagged).isEqualTo(1);

        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        assertThat(updated.getUpdatedAt()).isAfter(order.getUpdatedAt());
    }

    @Test
    @DisplayName("2. Đơn PAID chưa quá 15 phút -> không thay đổi")
    void test2_RecentPaidOrder_Under15Min_NotTagged() {
        // Đơn mới thanh toán 5 phút trước (< 15m)
        Order order = createPaidOrderWithAge(5);
        UUID orderId = order.getId();

        int tagged = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(tagged).isEqualTo(0);

        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("3. Đơn PAID quá hạn nhưng đã có dispense command -> không thay đổi")
    void test3_StalePaidOrder_WithExistingCommand_NotTagged() {
        Order order = createPaidOrderWithAge(25);
        UUID orderId = order.getId();

        // Tạo 1 command liên kết với order này
        DispenseCommand cmd = new DispenseCommand();
        cmd.setCommandId(UUID.randomUUID());
        cmd.setOrder(order);
        cmd.setMachine(testMachine);
        cmd.setSlotNumber(1);
        cmd.setStatus("PENDING");
        dispenseCommandRepository.save(cmd);

        int tagged = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(tagged).isEqualTo(0);

        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("4. Đơn ở trạng thái khác PAID -> không thay đổi")
    void test4_OtherOrderStatuses_NotTagged() {
        CreateOrderRequestDto req1 = new CreateOrderRequestDto("VM_TAGGER", testProduct.getId(), 1);
        Order o1 = orderRepository.findById(orderService.createOrder(req1).getOrderId()).orElseThrow();
        o1.setStatus(OrderStatus.COMPLETED);
        o1.setPaidAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        orderRepository.save(o1);

        CreateOrderRequestDto req2 = new CreateOrderRequestDto("VM_TAGGER", testProduct.getId(), 1);
        Order o2 = orderRepository.findById(orderService.createOrder(req2).getOrderId()).orElseThrow();
        o2.setStatus(OrderStatus.EXPIRED);
        o2.setPaidAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        orderRepository.save(o2);

        CreateOrderRequestDto req3 = new CreateOrderRequestDto("VM_TAGGER", testProduct.getId(), 1);
        Order o3 = orderRepository.findById(orderService.createOrder(req3).getOrderId()).orElseThrow();
        o3.setStatus(OrderStatus.DISPENSE_FAILED);
        o3.setPaidAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        orderRepository.save(o3);

        int tagged = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(tagged).isEqualTo(0);

        assertThat(orderRepository.findById(o1.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderRepository.findById(o2.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(orderRepository.findById(o3.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DISPENSE_FAILED);
    }

    @Test
    @DisplayName("5. paidAt null -> không thay đổi")
    void test5_PaidAtNull_NotTagged() {
        CreateOrderRequestDto req = new CreateOrderRequestDto("VM_TAGGER", testProduct.getId(), 1);
        Order o = orderRepository.findById(orderService.createOrder(req).getOrderId()).orElseThrow();
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(null);
        orderRepository.save(o);

        int tagged = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(tagged).isEqualTo(0);

        Order updated = orderRepository.findById(o.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("6. Chạy job nhiều lần -> idempotent, không cập nhật lặp")
    void test6_MultipleRuns_Idempotent() {
        Order order = createPaidOrderWithAge(20);
        UUID orderId = order.getId();

        // Lần chạy 1: chuyển sang NEEDS_RECONCILIATION
        int run1 = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(run1).isEqualTo(1);
        Order orderAfterRun1 = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfterRun1.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        Instant updatedAtRun1 = orderAfterRun1.getUpdatedAt();

        // Lần chạy 2: không có đơn nào cần chuyển
        int run2 = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(run2).isEqualTo(0);
        Order orderAfterRun2 = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfterRun2.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        assertThat(orderAfterRun2.getUpdatedAt()).isEqualTo(updatedAtRun1);
    }

    @Test
    @DisplayName("7. Reservation và stock KHÔNG ĐỔI sau khi gắn cờ đối soát")
    void test7_StockAndReservationPreserved() {
        Order order = createPaidOrderWithAge(25);
        UUID orderId = order.getId();

        Product prodBefore = productRepository.findById(testProduct.getId()).orElseThrow();
        int initialStock = prodBefore.getStockQuantity();
        int initialReserved = prodBefore.getReservedQuantity();

        reconciliationService.tagStalePaidOrdersForReconciliation();

        Product prodAfter = productRepository.findById(testProduct.getId()).orElseThrow();
        // Tồn kho và reservation phải được giữ nguyên 100%
        assertThat(prodAfter.getStockQuantity()).isEqualTo(initialStock);
        assertThat(prodAfter.getReservedQuantity()).isEqualTo(initialReserved);
    }

    @Test
    @DisplayName("8. Không tạo dispense command hoặc gửi lệnh thiết bị")
    void test8_NoDispenseCommandCreated() {
        Order order = createPaidOrderWithAge(30);
        UUID orderId = order.getId();

        reconciliationService.tagStalePaidOrdersForReconciliation();

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands).isEmpty();
        assertThat(simulatedDeviceAdapter.getProcessedCommandIds()).isEmpty();
    }

    @Test
    @DisplayName("9. Cạnh tranh đồng thời giữa nhiều lượt chạy Tagger: chỉ đúng 1 lần cập nhật thành công")
    void test9_ConcurrentTaggerRuns_HandledSafely() throws InterruptedException {
        Order order = createPaidOrderWithAge(20);
        UUID orderId = order.getId();

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalTagged = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int res = reconciliationService.tagStalePaidOrdersForReconciliation();
                    totalTagged.addAndGet(res);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(finished).isTrue();

        // Tổng số lần chuyển trạng thái thành công đúng bằng 1
        assertThat(totalTagged.get()).isEqualTo(1);
        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
    }

    @Test
    @DisplayName("10. Tagger và Recovery Poller tương tác an toàn: Tagger đánh dấu thì Recovery không nhả hàng")
    void test10_TaggerAndRecoveryPoller_InteractionSafe() {
        // Đơn quá hạn 20 phút (quá max-age 15 phút)
        Order order = createPaidOrderWithAge(20);
        UUID orderId = order.getId();

        // 1. Tagger chạy trước -> chuyển sang NEEDS_RECONCILIATION
        int tagged = reconciliationService.tagStalePaidOrdersForReconciliation();
        assertThat(tagged).isEqualTo(1);

        // 2. Recovery Poller chạy -> không thể nhả hàng cho đơn này
        int recovered = dispenseRecoveryService.recoverOrphanedPaidOrders();
        assertThat(recovered).isEqualTo(0);

        // Đơn vẫn ở NEEDS_RECONCILIATION, không có command
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        assertThat(dispenseCommandRepository.findByOrderId(orderId)).isEmpty();
    }
}
