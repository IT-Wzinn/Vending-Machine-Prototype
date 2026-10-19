package com.vendingmachine.dispense;

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
import com.vendingmachine.order.repository.OrderItemRepository;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.order.service.OrderService;
import com.vendingmachine.payment.dto.SePayWebhookDto;
import com.vendingmachine.payment.dto.SePayWebhookResponseDto;
import com.vendingmachine.payment.entity.PaymentTransaction;
import com.vendingmachine.payment.entity.VerificationStatus;
import com.vendingmachine.payment.repository.PaymentTransactionRepository;
import com.vendingmachine.payment.service.PaymentService;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests backed by a real PostgreSQL database instance.
 * Proves that pessimistic row-level locking, serialization, and idempotency
 * hold true under high concurrent load on the actual PostgreSQL relational engine.
 */
@SpringBootTest
@ActiveProfiles("test-postgres")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresDispenseAndOrderConcurrencyTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DispenseService dispenseService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

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
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private SimulatedDeviceAdapter simulatedDeviceAdapter;

    private Machine pgMachine;
    private Product pgProduct;

    @BeforeEach
    void setUp() {
        simulatedDeviceAdapter.resetState();
        simulatedDeviceAdapter.setAsyncMode(false);
        simulatedDeviceAdapter.setScenario(SimulationScenario.SUCCESS);

        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();

        pgMachine = new Machine(
                UUID.randomUUID(),
                "VM_PG_CONCURRENT",
                "Máy kiểm thử PostgreSQL Thật",
                MachineStatus.ACTIVE,
                null
        );
        pgMachine = machineRepository.save(pgMachine);

        pgProduct = new Product(
                UUID.randomUUID(),
                pgMachine,
                "Bò Húc RedBull 250ml",
                "Nước tăng lực",
                "https://example.com/redbull.jpg",
                15000,
                1,
                10,
                0,
                true
        );
        pgProduct = productRepository.save(pgProduct);
    }

    @AfterEach
    void tearDown() {
        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Xác minh Database Engine thực tế: Đang kết nối tới PostgreSQL thật (Không phải H2)")
    void verifyDatabaseEngineIsPostgreSQL() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String dbProductName = connection.getMetaData().getDatabaseProductName();
            String dbVersion = connection.getMetaData().getDatabaseProductVersion();
            assertThat(dbProductName).isEqualToIgnoringCase("PostgreSQL");
            assertThat(dbVersion).isNotEmpty();
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("PostgreSQL Concurrency — Tranh chấp đặt hàng khi chỉ còn 1 sản phẩm: Đúng 1 thread thành công, không oversell")
    void postgresOrderConcurrency_onlyOneWinsLastStock() throws InterruptedException {
        // Cập nhật tồn kho sản phẩm chỉ còn 1
        pgProduct.setStockQuantity(1);
        pgProduct.setReservedQuantity(0);
        pgProduct = productRepository.save(pgProduct);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successOrders = new AtomicInteger(0);
        AtomicInteger outOfStockFailures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    orderService.createOrder(new CreateOrderRequestDto("VM_PG_CONCURRENT", pgProduct.getId(), 1));
                    successOrders.incrementAndGet();
                } catch (Exception e) {
                    outOfStockFailures.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Trên PostgreSQL với Pessimistic Write Lock: Đúng 1 thread tạo đơn thành công, 9 thread còn lại bị chặn
        assertThat(successOrders.get()).isEqualTo(1);
        assertThat(outOfStockFailures.get()).isEqualTo(9);

        // Kiểm tra tồn kho PostgreSQL: stock=1, reserved=1, available=0
        Product fresh = productRepository.findById(pgProduct.getId()).orElseThrow();
        assertThat(fresh.getStockQuantity()).isEqualTo(1);
        assertThat(fresh.getReservedQuantity()).isEqualTo(1);
        assertThat(fresh.getAvailableStock()).isEqualTo(0);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("PostgreSQL Concurrency — 10 threads đồng thời trigger xuất hàng cho 1 đơn: Đúng 1 lệnh tạo, trừ kho đúng 1 lần")
    void postgresDispenseConcurrency_onlyOneCommandExecuted() throws InterruptedException {
        OrderResponseDto created = orderService.createOrder(new CreateOrderRequestDto("VM_PG_CONCURRENT", pgProduct.getId(), 1));
        Order order = orderRepository.findById(created.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successfulResponses = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    DispenseTriggerResponseDto res = dispenseService.triggerDispensing(order.getId());
                    if (res != null) {
                        successfulResponses.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // Kiểm tra Order trong PostgreSQL
        Order finishedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finishedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Kiểm tra DispenseCommand trong PostgreSQL: Chỉ đúng 1 command duy nhất được tạo
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(order.getId());
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("COMPLETED");

        // Kiểm tra tồn kho PostgreSQL: stock_quantity 10 -> 9, reserved_quantity 1 -> 0
        Product fresh = productRepository.findById(pgProduct.getId()).orElseThrow();
        assertThat(fresh.getStockQuantity()).isEqualTo(9);
        assertThat(fresh.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("PostgreSQL Concurrency — 10 threads đồng thời gửi webhook thanh toán SePay: Đúng 1 transaction VERIFIED được lưu")
    void postgresPaymentWebhookConcurrency_idempotentProcessing() throws InterruptedException {
        OrderResponseDto created = orderService.createOrder(new CreateOrderRequestDto("VM_PG_CONCURRENT", pgProduct.getId(), 1));
        Order order = orderRepository.findById(created.getOrderId()).orElseThrow();

        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(998877L);
        payload.setGateway("MBBank");
        payload.setTransactionDate("2026-10-19 10:30:00");
        payload.setAccountNumber("DEMO123456");
        payload.setTransferType("in");
        payload.setTransferAmount(order.getTotalAmountVnd());
        payload.setContent("Thanh toan don " + order.getTransferReference());
        payload.setCode(order.getTransferReference());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    SePayWebhookResponseDto res = paymentService.processSePayWebhook("Apikey test-sepay-secret-key", payload);
                    if (res != null && res.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Kiểm tra database PostgreSQL: Chỉ đúng 1 PaymentTransaction duy nhất được ghi nhận
        List<PaymentTransaction> txs = paymentTransactionRepository.findByOrderId(order.getId());
        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        // Order chuyển sang PAID
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
