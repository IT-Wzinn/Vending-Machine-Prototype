package com.vendingmachine.order;

import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.repository.MachineRepository;
import com.vendingmachine.order.dto.CreateOrderRequestDto;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.dto.OrderStatusResponseDto;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderItem;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrderIntegrationAndConcurrencyTest {

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

    private Machine testMachine;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();

        testMachine = new Machine(
                UUID.randomUUID(),
                "VM_TEST",
                "Máy kiểm thử Concurrency",
                MachineStatus.ACTIVE,
                null
        );
        testMachine = machineRepository.save(testMachine);

        testProduct = new Product(
                UUID.randomUUID(),
                testMachine,
                "Trà xanh không độ",
                "Giải khát",
                "https://example.com/tra-xanh.jpg",
                12000,
                1,
                5,
                0,
                true
        );
        testProduct = productRepository.save(testProduct);
    }

    @Test
    @DisplayName("Tạo đơn hàng lưu vào database, tính đúng giá và giữ chỗ tồn kho")
    void createOrder_Success_PersistsInDatabaseAndReservesStock() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM_TEST", testProduct.getId(), 1);

        OrderResponseDto response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getProduct().getName()).isEqualTo("Trà xanh không độ");
        assertThat(response.getProduct().getPriceVnd()).isEqualTo(12000);
        assertThat(response.getTotalAmountVnd()).isEqualTo(12000);
        assertThat(response.getPayment().getPaymentQrData()).isNotBlank();

        // Kiểm tra database Order
        Order order = orderRepository.findById(response.getOrderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotalAmountVnd()).isEqualTo(12000);
        assertThat(order.getPublicOrderCode()).startsWith("VM_TEST-");
        assertThat(order.getTransferReference()).startsWith("VM_TEST");

        // Kiểm tra OrderItem
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(1);
        assertThat(items.get(0).getUnitPriceVnd()).isEqualTo(12000);

        // Kiểm tra Product reservedQuantity
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(1);
        assertThat(updatedProduct.getAvailableStock()).isEqualTo(4);
    }

    @Test
    @DisplayName("Concurrency: 10 luồng cùng đặt sản phẩm chỉ còn 1 tồn kho, duy nhất 1 đơn thành công và 9 đơn nhận PRODUCT_OUT_OF_STOCK")
    void concurrency_CreateOrder_LastStock_OnlyOneSucceeds() throws Exception {
        // Cập nhật tồn kho còn đúng 1 sản phẩm
        testProduct.setStockQuantity(1);
        testProduct.setReservedQuantity(0);
        testProduct = productRepository.save(testProduct);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);
        List<OrderResponseDto> successfulOrders = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Chờ tất cả luồng sẵn sàng để bắn request đồng thời
                    CreateOrderRequestDto request = new CreateOrderRequestDto("VM_TEST", testProduct.getId(), 1);
                    OrderResponseDto result = orderService.createOrder(request);
                    successCount.incrementAndGet();
                    successfulOrders.add(result);
                } catch (BusinessException be) {
                    if (ErrorCode.PRODUCT_OUT_OF_STOCK.equals(be.getErrorCode())) {
                        outOfStockCount.incrementAndGet();
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Bắn đồng thời
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(outOfStockCount.get()).isEqualTo(9);
        assertThat(otherErrors.get()).isEqualTo(0);

        // Xác minh trạng thái cuối cùng của database
        Product reloadedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloadedProduct.getStockQuantity()).isEqualTo(1);
        assertThat(reloadedProduct.getReservedQuantity()).isEqualTo(1);
        assertThat(reloadedProduct.getAvailableStock()).isEqualTo(0);

        long totalOrdersInDb = orderRepository.count();
        assertThat(totalOrdersInDb).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrency: Hai luồng cùng xử lý expiration trên một đơn hàng, giữ chỗ chỉ được giải phóng đúng 1 lần")
    void concurrency_OrderExpiration_ReleasedStockOnlyOnce() throws Exception {
        // Tạo đơn hàng ở quá khứ đã hết hạn
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPublicOrderCode("VM_TEST-20261018-0999");
        order.setMachine(testMachine);
        order.setProduct(testProduct);
        order.setQuantity(1);
        order.setUnitPriceVnd(12000);
        order.setTotalAmountVnd(12000);
        order.setTransferReference("VMTESTREF0999");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentExpiresAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        order.setCreatedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        order.setUpdatedAt(Instant.now().minus(15, ChronoUnit.MINUTES));
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setId(UUID.randomUUID());
        orderItem.setOrder(order);
        orderItem.setProduct(testProduct);
        orderItem.setQuantity(1);
        orderItem.setUnitPriceVnd(12000);
        orderItem.setSubtotalVnd(12000);
        orderItemRepository.save(orderItem);

        // Đặt giữ chỗ của product lên 1
        testProduct.setStockQuantity(5);
        testProduct.setReservedQuantity(1);
        testProduct = productRepository.save(testProduct);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        UUID orderIdToTest = order.getId();
        List<OrderStatusResponseDto> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    OrderStatusResponseDto status = orderService.getOrderStatus(orderIdToTest);
                    synchronized (results) {
                        results.add(status);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(2);
        for (OrderStatusResponseDto res : results) {
            assertThat(res.getStatus()).isEqualTo("EXPIRED");
        }

        // Đơn hàng trong database chuyển sang EXPIRED
        Order expiredOrder = orderRepository.findById(orderIdToTest).orElseThrow();
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        // Reserved quantity phải giảm đúng 1 lần (từ 1 về 0), KHÔNG được giảm âm (-1)
        Product reloadedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloadedProduct.getReservedQuantity()).isEqualTo(0);
        assertThat(reloadedProduct.getAvailableStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Scheduled Job: cleanupExpiredOrders quét và chuyển trạng thái đơn hết hạn thành công")
    void scheduledJob_ExpireOverdueOrders_Success() {
        Order overdueOrder = new Order();
        overdueOrder.setId(UUID.randomUUID());
        overdueOrder.setPublicOrderCode("VM_TEST-20261018-0998");
        overdueOrder.setMachine(testMachine);
        overdueOrder.setProduct(testProduct);
        overdueOrder.setQuantity(1);
        overdueOrder.setUnitPriceVnd(12000);
        overdueOrder.setTotalAmountVnd(12000);
        overdueOrder.setTransferReference("VMTESTREF0998");
        overdueOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        overdueOrder.setPaymentExpiresAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        overdueOrder.setCreatedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        overdueOrder.setUpdatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        overdueOrder = orderRepository.save(overdueOrder);

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(overdueOrder);
        item.setProduct(testProduct);
        item.setQuantity(1);
        item.setUnitPriceVnd(12000);
        item.setSubtotalVnd(12000);
        orderItemRepository.save(item);

        testProduct.setStockQuantity(5);
        testProduct.setReservedQuantity(1);
        productRepository.save(testProduct);

        // Gọi cleanup job
        int count = orderService.cleanupExpiredOrders();

        assertThat(count).isGreaterThanOrEqualTo(1);

        Order updatedOrder = orderRepository.findById(overdueOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);
    }
}
