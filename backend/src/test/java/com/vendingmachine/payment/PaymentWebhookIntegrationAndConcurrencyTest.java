package com.vendingmachine.payment;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentWebhookIntegrationAndConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

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

    @Value("${app.payment.sepay.api-key}")
    private String configuredApiKey;

    @Autowired(required = false)
    private com.vendingmachine.dispense.repository.DispenseCommandRepository dispenseCommandRepository;

    @Autowired(required = false)
    private com.vendingmachine.device.repository.DeviceEventRepository deviceEventRepository;

    private Machine testMachine;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        if (deviceEventRepository != null) deviceEventRepository.deleteAll();
        if (dispenseCommandRepository != null) dispenseCommandRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();

        testMachine = new Machine(
                UUID.randomUUID(),
                "VM_PAY",
                "Máy kiểm thử Thanh toán Concurrency",
                MachineStatus.ACTIVE,
                null
        );
        testMachine = machineRepository.save(testMachine);

        testProduct = new Product(
                UUID.randomUUID(),
                testMachine,
                "Cà phê sữa đá",
                "Đồ uống",
                "https://example.com/cf-sua.jpg",
                15000,
                1,
                10,
                0,
                true
        );
        testProduct = productRepository.save(testProduct);
    }

    private SePayWebhookDto createWebhookPayload(Long id, int amount, String content, String code) {
        SePayWebhookDto dto = new SePayWebhookDto();
        dto.setId(id);
        dto.setGateway("MBBank");
        dto.setAccountNumber("0987654321");
        dto.setTransactionDate("2026-10-19 09:00:00");
        dto.setTransferAmount(amount);
        dto.setTransferType("in");
        dto.setContent(content);
        dto.setCode(code);
        dto.setReferenceCode("REF" + id);
        return dto;
    }

    @Test
    @DisplayName("10 webhook gửi lặp đồng thời cho cùng một giao dịch: Xử lý an toàn, chỉ 1 transaction được lưu, order là PAID, tồn kho vật lý không đổi")
    void concurrentDuplicateWebhooks_HandledIdempotentlyWithoutConstraintViolation() throws InterruptedException {
        // Tạo đơn hàng
        CreateOrderRequestDto createReq = new CreateOrderRequestDto("VM_PAY", testProduct.getId(), 1);
        OrderResponseDto orderDto = orderService.createOrder(createReq);

        Order order = orderRepository.findById(orderDto.getOrderId()).orElseThrow();
        String authHeader = "Apikey " + configuredApiKey;
        long providerTxId = 99001L;

        SePayWebhookDto webhookDto = createWebhookPayload(
                providerTxId,
                order.getTotalAmountVnd(),
                "THANH TOAN DON " + order.getTransferReference() + " TAI MAY",
                order.getTransferReference()
        );

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successResponses = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    SePayWebhookResponseDto res = paymentService.processSePayWebhook(authHeader, webhookDto);
                    if (res != null && res.isSuccess()) {
                        successResponses.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(failures.get()).isEqualTo(0);
        assertThat(successResponses.get()).isEqualTo(threadCount);

        // Kiểm tra database: Đúng 1 PaymentTransaction được ghi nhận
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByOrderId(order.getId());
        assertThat(transactions).hasSize(1);
        PaymentTransaction tx = transactions.get(0);
        assertThat(tx.getProviderTransactionId()).isEqualTo(String.valueOf(providerTxId));
        assertThat(tx.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(tx.getAmountVnd()).isEqualTo(order.getTotalAmountVnd());

        // Kiểm tra Order: chuyển sang PAID, có paidAt
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updatedOrder.getPaidAt()).isNotNull();

        // QUY TẮC PHASE 5: Tồn kho vật lý (stock_quantity) KHÔNG ĐƯỢC GIẢM tại Phase 5!
        // Giữ chỗ reservedQuantity vẫn là 1 để chờ Phase 6 nhả hàng vật lý
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(10);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(1);
        assertThat(updatedProduct.getAvailableStock()).isEqualTo(9);
    }

    @Test
    @DisplayName("Cạnh tranh giữa Webhook thanh toán và Job hết hạn: Trạng thái nhất quán, không xảy ra xung đột dữ liệu")
    void concurrencyRace_PaymentWebhookVsOrderExpiration() throws InterruptedException {
        // Tạo đơn hàng
        CreateOrderRequestDto createReq = new CreateOrderRequestDto("VM_PAY", testProduct.getId(), 1);
        OrderResponseDto orderDto = orderService.createOrder(createReq);
        Order order = orderRepository.findById(orderDto.getOrderId()).orElseThrow();

        String authHeader = "Apikey " + configuredApiKey;
        long providerTxId = 99002L;

        SePayWebhookDto webhookDto = createWebhookPayload(
                providerTxId,
                order.getTotalAmountVnd(),
                "THANH TOAN " + order.getTransferReference(),
                order.getTransferReference()
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<Throwable> webhookErr = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> expireErr = new java.util.concurrent.atomic.AtomicReference<>();

        executor.submit(() -> {
            try {
                startGate.await();
                paymentService.processSePayWebhook(authHeader, webhookDto);
            } catch (Throwable t) {
                webhookErr.set(t);
            } finally {
                endGate.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startGate.await();
                orderService.expireOrderIfPending(order.getId());
            } catch (Throwable t) {
                expireErr.set(t);
            } finally {
                endGate.countDown();
            }
        });

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(webhookErr.get()).isNull();
        assertThat(expireErr.get()).isNull();

        // Trạng thái kết thúc phải là một trong 2 kịch bản hợp lệ nhất quán:
        // Kịch bản A: Webhook đến trước -> Order là PAID, Transaction là VERIFIED, reserved_quantity = 1
        // Kịch bản B: Expiration đến trước -> Order hết hạn rồi Webhook tới -> Order là NEEDS_RECONCILIATION,
        //             Transaction là LATE_PAYMENT, reserved_quantity = 0 (đã giải phóng khi hết hạn)
        Order freshOrder = orderRepository.findById(order.getId()).orElseThrow();
        Product freshProduct = productRepository.findById(testProduct.getId()).orElseThrow();

        if (freshOrder.getStatus() == OrderStatus.PAID) {
            assertThat(freshProduct.getReservedQuantity()).isEqualTo(1);
            assertThat(freshProduct.getStockQuantity()).isEqualTo(10);
            PaymentTransaction tx = paymentTransactionRepository.findByProviderAndProviderTransactionId("SEPAY", String.valueOf(providerTxId)).orElseThrow();
            assertThat(tx.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        } else {
            assertThat(freshOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
            assertThat(freshProduct.getReservedQuantity()).isEqualTo(0);
            assertThat(freshProduct.getStockQuantity()).isEqualTo(10);
            PaymentTransaction tx = paymentTransactionRepository.findByProviderAndProviderTransactionId("SEPAY", String.valueOf(providerTxId)).orElseThrow();
            assertThat(tx.getVerificationStatus()).isEqualTo(VerificationStatus.LATE_PAYMENT);
        }
    }

    @Test
    @DisplayName("Webhook chuyển tiền thiếu: Ghi nhận UNDERPAID, không chuyển đơn sang PAID, không nhả hàng")
    void underpaidWebhook_RecordedAsUnderpaidAndOrderRemainsPending() {
        CreateOrderRequestDto createReq = new CreateOrderRequestDto("VM_PAY", testProduct.getId(), 1);
        OrderResponseDto orderDto = orderService.createOrder(createReq);
        Order order = orderRepository.findById(orderDto.getOrderId()).orElseThrow();

        String authHeader = "Apikey " + configuredApiKey;
        long providerTxId = 99003L;

        // Số tiền thiếu: 10,000 VND thay vì 15,000 VND
        SePayWebhookDto webhookDto = createWebhookPayload(
                providerTxId,
                10000,
                "CHUYEN THIEU TIEN " + order.getTransferReference(),
                order.getTransferReference()
        );

        SePayWebhookResponseDto res = paymentService.processSePayWebhook(authHeader, webhookDto);
        assertThat(res.isSuccess()).isTrue();

        PaymentTransaction tx = paymentTransactionRepository.findByProviderAndProviderTransactionId("SEPAY", String.valueOf(providerTxId)).orElseThrow();
        assertThat(tx.getVerificationStatus()).isEqualTo(VerificationStatus.UNDERPAID);
        assertThat(tx.getAmountVnd()).isEqualTo(10000);
        assertThat(tx.getOrder().getId()).isEqualTo(order.getId());

        Order freshOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(freshOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(freshOrder.getPaidAt()).isNull();

        // Tồn kho không thay đổi
        Product freshProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(freshProduct.getStockQuantity()).isEqualTo(10);
        assertThat(freshProduct.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Webhook không khớp mã tham chiếu: Ghi nhận UNMATCHED, không gắn order, không chuyển trạng thái")
    void unmatchedTransferReference_RecordedAsUnmatched() {
        String authHeader = "Apikey " + configuredApiKey;
        long providerTxId = 99004L;

        SePayWebhookDto webhookDto = createWebhookPayload(
                providerTxId,
                50000,
                "CHUYEN TIEN UNG HO KHONG CO MA THAM CHIEU",
                null
        );

        SePayWebhookResponseDto res = paymentService.processSePayWebhook(authHeader, webhookDto);
        assertThat(res.isSuccess()).isTrue();

        PaymentTransaction tx = paymentTransactionRepository.findByProviderAndProviderTransactionId("SEPAY", String.valueOf(providerTxId)).orElseThrow();
        assertThat(tx.getVerificationStatus()).isEqualTo(VerificationStatus.UNMATCHED);
        assertThat(tx.getOrder()).isNull();
        assertThat(tx.getAmountVnd()).isEqualTo(50000);
    }
}
