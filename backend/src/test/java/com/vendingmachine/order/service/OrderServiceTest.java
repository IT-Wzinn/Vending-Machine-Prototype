package com.vendingmachine.order.service;

import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.repository.MachineRepository;
import com.vendingmachine.order.dto.CreateOrderRequestDto;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.dto.OrderStatusResponseDto;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MachineRepository machineRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Machine activeMachine;
    private Machine inactiveMachine;
    private Product inStockProduct;
    private Product outOfStockProduct;
    private UUID productId;
    private UUID machineId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "expirationMinutes", 15);
        ReflectionTestUtils.setField(orderService, "demoBankName", "DEMO Bank");
        ReflectionTestUtils.setField(orderService, "demoAccountNumber", "DEMO123456");
        ReflectionTestUtils.setField(orderService, "demoAccountName", "DEMO SMART VENDING");

        machineId = UUID.randomUUID();
        activeMachine = new Machine();
        activeMachine.setId(machineId);
        activeMachine.setMachineCode("VM001");
        activeMachine.setName("Máy tầng 1");
        activeMachine.setStatus(MachineStatus.ACTIVE);

        inactiveMachine = new Machine();
        inactiveMachine.setId(UUID.randomUUID());
        inactiveMachine.setMachineCode("VM002");
        inactiveMachine.setName("Máy tầng 2");
        inactiveMachine.setStatus(MachineStatus.INACTIVE);

        productId = UUID.randomUUID();
        inStockProduct = new Product();
        inStockProduct.setId(productId);
        inStockProduct.setMachine(activeMachine);
        inStockProduct.setName("Nước suối Aqua");
        inStockProduct.setPriceVnd(8000);
        inStockProduct.setSlotNumber(1);
        inStockProduct.setStockQuantity(5);
        inStockProduct.setReservedQuantity(0);
        inStockProduct.setIsActive(true);

        outOfStockProduct = new Product();
        outOfStockProduct.setId(UUID.randomUUID());
        outOfStockProduct.setMachine(activeMachine);
        outOfStockProduct.setName("Kẹo cao su");
        outOfStockProduct.setPriceVnd(5000);
        outOfStockProduct.setSlotNumber(3);
        outOfStockProduct.setStockQuantity(1);
        outOfStockProduct.setReservedQuantity(1); // 1 - 1 = 0 available
        outOfStockProduct.setIsActive(true);
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công: kiểm tra giá zero-trust, giữ chỗ tồn kho và mã duy nhất")
    void createOrder_success() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", productId, 1);

        when(machineRepository.findByMachineCode("VM001")).thenReturn(Optional.of(activeMachine));
        when(productRepository.findByIdAndMachineCodeForUpdate(productId, "VM001")).thenReturn(Optional.of(inStockProduct));
        when(orderRepository.existsByPublicOrderCode(anyString())).thenReturn(false);
        when(orderRepository.existsByTransferReference(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseDto response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getMachineId()).isEqualTo("VM001");
        assertThat(response.getProduct().getProductId()).isEqualTo(productId);
        assertThat(response.getProduct().getName()).isEqualTo("Nước suối Aqua");
        // Zero-trust price verification: price from DB (8000), not from client
        assertThat(response.getProduct().getPriceVnd()).isEqualTo(8000);
        assertThat(response.getQuantity()).isEqualTo(1);
        assertThat(response.getTotalAmountVnd()).isEqualTo(8000);
        assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getPublicOrderCode()).startsWith("VM001-");
        assertThat(response.getPayment().getTransferReference()).startsWith("VM001");
        assertThat(response.getPayment().getAmountVnd()).isEqualTo(8000);
        assertThat(response.getPayment().getBankAccountNumber()).isEqualTo("DEMO123456");

        // Verify stock reservation incremented
        assertThat(inStockProduct.getReservedQuantity()).isEqualTo(1);
        verify(productRepository).save(inStockProduct);

        // Verify Order entity saved with item
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getUnitPriceVnd()).isEqualTo(8000);
        assertThat(saved.getItems().get(0).getSubtotalVnd()).isEqualTo(8000);
    }

    @Test
    @DisplayName("Tạo đơn từ chối khi số lượng khác 1 (MVP constraint): INVALID_QUANTITY (400)")
    void createOrder_invalidQuantity_throwsBadRequest() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", productId, 2);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(machineRepository, productRepository, orderRepository);
    }

    @Test
    @DisplayName("Tạo đơn từ chối khi máy không tồn tại: MACHINE_NOT_FOUND (404)")
    void createOrder_machineNotFound_throwsNotFound() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM999", productId, 1);
        when(machineRepository.findByMachineCode("VM999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MACHINE_NOT_FOUND);

        verify(machineRepository).findByMachineCode("VM999");
        verifyNoInteractions(productRepository, orderRepository);
    }

    @Test
    @DisplayName("Tạo đơn từ chối khi máy tạm ngừng hoạt động: MACHINE_INACTIVE (422)")
    void createOrder_machineInactive_throwsUnprocessableEntity() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM002", productId, 1);
        when(machineRepository.findByMachineCode("VM002")).thenReturn(Optional.of(inactiveMachine));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MACHINE_INACTIVE)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.UNPROCESSABLE_ENTITY);

        verifyNoInteractions(productRepository, orderRepository);
    }

    @Test
    @DisplayName("Tạo đơn từ chối khi sản phẩm không tồn tại: PRODUCT_NOT_FOUND (404)")
    void createOrder_productNotFound_throwsNotFound() {
        UUID unknownProdId = UUID.randomUUID();
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", unknownProdId, 1);

        when(machineRepository.findByMachineCode("VM001")).thenReturn(Optional.of(activeMachine));
        when(productRepository.findById(unknownProdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(productRepository).findById(unknownProdId);
    }

    @Test
    @DisplayName("Tạo đơn từ chối khi sản phẩm không thuộc máy: PRODUCT_NOT_IN_MACHINE (422)")
    void createOrder_productNotInMachine_throwsUnprocessableEntity() {
        Product otherMachineProduct = new Product();
        otherMachineProduct.setId(productId);
        otherMachineProduct.setMachine(inactiveMachine); // Belongs to VM002, not VM001

        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", productId, 1);
        when(machineRepository.findByMachineCode("VM001")).thenReturn(Optional.of(activeMachine));
        when(productRepository.findById(productId)).thenReturn(Optional.of(otherMachineProduct));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_IN_MACHINE)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Tạo đơn từ chối khi sản phẩm hết hàng hoặc hết tồn khả dụng: PRODUCT_OUT_OF_STOCK (409)")
    void createOrder_outOfStock_throwsConflict() {
        UUID outProdId = outOfStockProduct.getId();
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", outProdId, 1);

        when(machineRepository.findByMachineCode("VM001")).thenReturn(Optional.of(activeMachine));
        when(productRepository.findByIdAndMachineCodeForUpdate(outProdId, "VM001")).thenReturn(Optional.of(outOfStockProduct));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_OUT_OF_STOCK)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);

        // Reserved quantity must not change
        assertThat(outOfStockProduct.getReservedQuantity()).isEqualTo(1);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng: trả thông tin hợp lệ")
    void getOrder_success() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setPublicOrderCode("VM001-20261018-AB3F");
        order.setMachine(activeMachine);
        order.setProduct(inStockProduct);
        order.setQuantity(1);
        order.setUnitPriceVnd(8000);
        order.setTotalAmountVnd(8000);
        order.setTransferReference("VM001AB3F");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponseDto dto = orderService.getOrder(orderId);

        assertThat(dto).isNotNull();
        assertThat(dto.getOrderId()).isEqualTo(orderId);
        assertThat(dto.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(dto.getProduct().getName()).isEqualTo("Nước suối Aqua");
        assertThat(dto.getPayment().getTransferReference()).isEqualTo("VM001AB3F");
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng khi quá hạn: tự động chuyển EXPIRED và giải phóng giữ chỗ")
    void getOrder_expiredHandled() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setMachine(activeMachine);
        inStockProduct.setReservedQuantity(1);
        order.setProduct(inStockProduct);
        order.setQuantity(1);
        order.setUnitPriceVnd(8000);
        order.setTotalAmountVnd(8000);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        // Expired 5 minutes ago
        order.setPaymentExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        order.setCreatedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        order.setUpdatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.updateOrderStatusIfExpected(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.EXPIRED), any(Instant.class)))
                .thenReturn(1);
        when(productRepository.findById(productId)).thenReturn(Optional.of(inStockProduct));

        OrderResponseDto dto = orderService.getOrder(orderId);

        assertThat(dto).isNotNull();
        // Reserved quantity decreased back to 0
        assertThat(inStockProduct.getReservedQuantity()).isEqualTo(0);
        verify(productRepository).save(inStockProduct);
    }

    @Test
    @DisplayName("Lấy trạng thái đơn hàng (polling): trả về status và message phù hợp")
    void getOrderStatus_success() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        order.setUpdatedAt(Instant.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderStatusResponseDto statusDto = orderService.getOrderStatus(orderId);

        assertThat(statusDto).isNotNull();
        assertThat(statusDto.getOrderId()).isEqualTo(orderId);
        assertThat(statusDto.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(statusDto.getMessage()).isNull();
    }

    @Test
    @DisplayName("Hai luồng cùng expire một đơn: chỉ giải phóng giữ chỗ đúng 1 lần (Idempotent Expiration)")
    void expireOrderIfPending_releasesStockOnce() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        inStockProduct.setReservedQuantity(1);
        order.setProduct(inStockProduct);
        order.setQuantity(1);
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        // Thread 1: updates 1 row
        when(orderRepository.updateOrderStatusIfExpected(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.EXPIRED), any(Instant.class)))
                .thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.of(inStockProduct));

        boolean thread1Result = orderService.expireOrderIfPending(orderId);
        assertThat(thread1Result).isTrue();
        assertThat(inStockProduct.getReservedQuantity()).isEqualTo(0);
        verify(productRepository, times(1)).save(inStockProduct);

        // Thread 2: order is already EXPIRED, returns 0 rows updated
        when(orderRepository.updateOrderStatusIfExpected(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.EXPIRED), any(Instant.class)))
                .thenReturn(0);

        boolean thread2Result = orderService.expireOrderIfPending(orderId);
        assertThat(thread2Result).isFalse();
        // Product save must still be called only once
        verify(productRepository, times(1)).save(inStockProduct);
    }

    @Test
    @DisplayName("Scheduled cleanup quét và giải phóng các đơn PENDING_PAYMENT quá hạn")
    void cleanupExpiredOrders_success() {
        UUID orderId = UUID.randomUUID();
        Order expiredOrder = new Order();
        expiredOrder.setId(orderId);
        expiredOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        expiredOrder.setPaymentExpiresAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        expiredOrder.setProduct(inStockProduct);
        inStockProduct.setReservedQuantity(1);
        expiredOrder.setQuantity(1);

        when(orderRepository.findByStatusAndPaymentExpiresAtBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of(expiredOrder));
        when(orderRepository.updateOrderStatusIfExpected(eq(orderId), eq(OrderStatus.PENDING_PAYMENT), eq(OrderStatus.EXPIRED), any(Instant.class)))
                .thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(expiredOrder));
        when(productRepository.findById(productId)).thenReturn(Optional.of(inStockProduct));

        int expiredCount = orderService.cleanupExpiredOrders();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(inStockProduct.getReservedQuantity()).isEqualTo(0);
        verify(productRepository).save(inStockProduct);
    }
}
