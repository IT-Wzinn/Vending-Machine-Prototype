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
import com.vendingmachine.order.entity.OrderItem;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.payment.service.PaymentService;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC"));

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MachineRepository machineRepository;
    private final PaymentService paymentService;

    @Value("${app.order.expiration-minutes:15}")
    private int expirationMinutes;

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductRepository productRepository,
                            MachineRepository machineRepository,
                            PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.machineRepository = machineRepository;
        this.paymentService = paymentService;
    }

    @Override
    @Transactional
    public OrderResponseDto createOrder(CreateOrderRequestDto request) {
        // 1. Validate MVP Quantity rule
        if (request.getQuantity() == null || request.getQuantity() != 1) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY, "Chỉ có thể đặt 1 sản phẩm mỗi lần.", HttpStatus.BAD_REQUEST);
        }

        // 2. Validate Machine
        Machine machine = machineRepository.findByMachineCode(request.getMachineId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.MACHINE_NOT_FOUND, "Máy bán hàng không tồn tại."));

        if (machine.getStatus() != MachineStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MACHINE_INACTIVE, "Máy bán hàng hiện đang tạm ngừng hoạt động.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 3. Acquire Pessimistic Write Lock on Product first to prevent overselling and avoid stale L1 session cache
        Product product = productRepository.findByIdAndMachineCodeForUpdate(request.getProductId(), machine.getMachineCode())
                .orElse(null);

        if (product == null) {
            // Check if product exists in another machine or doesn't exist
            Optional<Product> anyProduct = productRepository.findById(request.getProductId());
            if (anyProduct.isEmpty()) {
                throw new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại.");
            }
            Product p = anyProduct.get();
            if (!p.getMachine().getMachineCode().equalsIgnoreCase(request.getMachineId())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_IN_MACHINE, "Sản phẩm không có tại máy này.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            throw new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại.");
        }

        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại.");
        }

        if (product.getAvailableStock() < request.getQuantity()) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK, "Sản phẩm này hiện đã hết hàng.", HttpStatus.CONFLICT);
        }

        // 4. Zero-trust pricing: read price directly from database
        int unitPriceVnd = product.getPriceVnd();
        int totalAmountVnd = unitPriceVnd * request.getQuantity();

        // 5. Generate unique publicOrderCode and transferReference with collision retry
        String publicOrderCode = generateUniquePublicOrderCode(machine.getMachineCode());
        String transferReference = generateUniqueTransferReference(machine.getMachineCode());

        // 6. Reserve stock
        product.setReservedQuantity(product.getReservedQuantity() + request.getQuantity());
        productRepository.save(product);

        // 7. Create Order & OrderItem
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(expirationMinutes));

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPublicOrderCode(publicOrderCode);
        order.setMachine(machine);
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setUnitPriceVnd(unitPriceVnd);
        order.setTotalAmountVnd(totalAmountVnd);
        order.setTransferReference(transferReference);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentExpiresAt(expiresAt);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        OrderItem orderItem = new OrderItem();
        orderItem.setId(UUID.randomUUID());
        orderItem.setProduct(product);
        orderItem.setQuantity(request.getQuantity());
        orderItem.setUnitPriceVnd(unitPriceVnd);
        orderItem.setSubtotalVnd(totalAmountVnd);
        order.addItem(orderItem);

        Order savedOrder = orderRepository.save(order);
        log.info("Created order {} (code: {}, transferRef: {}, amount: {} VND)",
                savedOrder.getId(), savedOrder.getPublicOrderCode(), savedOrder.getTransferReference(), savedOrder.getTotalAmountVnd());

        // 8. Build payment instructions (via PaymentService with VietQR)
        OrderResponseDto.OrderPaymentDto paymentDto = paymentService.createPaymentInstructions(savedOrder);
        return mapToOrderResponseDto(savedOrder, paymentDto, null);
    }

    @Override
    @Transactional
    public OrderResponseDto getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Đơn hàng không tồn tại."));

        // Check and handle expiration if pending and time passed
        checkAndHandleExpiration(order);

        OrderResponseDto.OrderPaymentDto paymentDto = paymentService.createPaymentInstructions(order);
        OrderResponseDto.OrderDispensingDto dispensingDto = buildDispensingDto(order);

        return mapToOrderResponseDto(order, paymentDto, dispensingDto);
    }

    @Override
    @Transactional
    public OrderStatusResponseDto getOrderStatus(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Đơn hàng không tồn tại."));

        // Check and handle expiration if pending and time passed
        checkAndHandleExpiration(order);

        String message = determineCustomerStatusMessage(order);

        return new OrderStatusResponseDto(
                order.getId(),
                order.getStatus().name(),
                order.getUpdatedAt(),
                message
        );
    }

    @Override
    @Transactional
    public boolean expireOrderIfPending(UUID orderId) {
        Instant now = Instant.now();
        // Atomic conditional update ensures only ONE concurrent thread executes status transition
        int updatedRows = orderRepository.updateOrderStatusIfExpected(
                orderId,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.EXPIRED,
                now
        );

        if (updatedRows > 0) {
            // Exactly one thread wins the transition; release reserved stock
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order != null) {
                if (order.isReservationHeld()) {
                    order.setReservationHeld(false);
                    orderRepository.save(order);

                    if (order.getProduct() != null) {
                        UUID productId = order.getProduct().getId();
                        Product product = productRepository.findByIdForUpdate(productId)
                                .orElseGet(() -> productRepository.findById(productId).orElse(null));
                        if (product != null) {
                            int qty = order.getQuantity() != null ? order.getQuantity() : 1;
                            if (product.getReservedQuantity() >= qty) {
                                product.setReservedQuantity(product.getReservedQuantity() - qty);
                                productRepository.save(product);
                                log.info("Released stock reservation for expired order {} (product: {}, newReserved: {})",
                                        orderId, product.getName(), product.getReservedQuantity());
                            } else {
                                log.error("INVENTORY INCONSISTENCY: Product {} has reservedQuantity={} less than order quantity {}",
                                        productId, product.getReservedQuantity(), qty);
                            }
                        }
                    }
                }
            }
            return true;
        }

        // Already transitioned or not in PENDING_PAYMENT
        return false;
    }

    @Override
    @Scheduled(fixedDelayString = "${app.order.cleanup-interval-ms:60000}")
    @Transactional
    public int cleanupExpiredOrders() {
        Instant now = Instant.now();
        List<Order> expiredOrders = orderRepository.findByStatusAndPaymentExpiresAtBefore(
                OrderStatus.PENDING_PAYMENT, now);

        if (expiredOrders.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Order order : expiredOrders) {
            boolean expired = expireOrderIfPending(order.getId());
            if (expired) {
                count++;
            }
        }

        if (count > 0) {
            log.info("Scheduled cleanup expired {} pending orders", count);
        }
        return count;
    }

    private void checkAndHandleExpiration(Order order) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT &&
                Instant.now().isAfter(order.getPaymentExpiresAt())) {
            boolean transitioned = expireOrderIfPending(order.getId());
            if (transitioned) {
                order.setStatus(OrderStatus.EXPIRED);
                order.setUpdatedAt(Instant.now());
            } else {
                orderRepository.findById(order.getId()).ifPresent(fresh -> {
                    order.setStatus(fresh.getStatus());
                    order.setUpdatedAt(fresh.getUpdatedAt());
                });
            }
        }
    }

    private String generateUniquePublicOrderCode(String machineCode) {
        String datePart = DATE_FORMATTER.format(Instant.now());
        for (int i = 0; i < 5; i++) {
            String suffix = randomHexString(4);
            String code = String.format("%s-%s-%s", machineCode, datePart, suffix);
            if (!orderRepository.existsByPublicOrderCode(code)) {
                return code;
            }
        }
        // Fallback with timestamp millisecond
        return String.format("%s-%s-%04X", machineCode, datePart, RANDOM.nextInt(0x10000));
    }

    private String generateUniqueTransferReference(String machineCode) {
        for (int i = 0; i < 5; i++) {
            String suffix = randomAlphanumericString(4);
            String ref = String.format("%s%s", machineCode, suffix);
            if (!orderRepository.existsByTransferReference(ref)) {
                return ref;
            }
        }
        return String.format("%s%04X", machineCode, RANDOM.nextInt(0x10000));
    }

    private String randomHexString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)).toUpperCase());
        }
        return sb.toString();
    }

    private String randomAlphanumericString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }



    private OrderResponseDto.OrderDispensingDto buildDispensingDto(Order order) {
        String result = null;
        String verificationLevel = null;
        String message = null;

        if (order.getStatus() == OrderStatus.COMPLETED) {
            result = "SUCCESS";
            verificationLevel = "MOTOR_CYCLE_ONLY";
            message = "Sản phẩm đã được nhả ra. Vui lòng lấy hàng tại ngăn dưới.";
        } else if (order.getStatus() == OrderStatus.DISPENSE_FAILED) {
            result = "FAILURE";
            verificationLevel = "MOTOR_CYCLE_ONLY";
            message = "Máy có thể gặp sự cố khi nhả hàng. Vui lòng liên hệ nhân viên hỗ trợ và không thanh toán thêm lần nữa.";
        } else if (order.getStatus() == OrderStatus.NEEDS_RECONCILIATION) {
            result = null;
            verificationLevel = "MOTOR_CYCLE_ONLY";
            message = "Đơn hàng của bạn đang được kiểm tra. Vui lòng không thanh toán thêm lần nữa và liên hệ nhân viên nếu cần hỗ trợ.";
        } else if (order.getStatus() == OrderStatus.DISPENSING) {
            message = "Đang chuẩn bị sản phẩm cho bạn...";
        }

        return new OrderResponseDto.OrderDispensingDto(
                order.getDispensingStartedAt(),
                order.getCompletedAt(),
                result,
                verificationLevel,
                message
        );
    }

    private String determineCustomerStatusMessage(Order order) {
        return switch (order.getStatus()) {
            case PAID -> "Thanh toán thành công. Đang chuẩn bị sản phẩm cho bạn...";
            case EXPIRED -> "Đơn hàng đã hết hạn thanh toán.";
            case DISPENSE_FAILED -> "Máy có thể gặp sự cố khi nhả hàng. Vui lòng liên hệ nhân viên hỗ trợ và không thanh toán thêm lần nữa.";
            case NEEDS_RECONCILIATION -> "Đơn hàng của bạn đang được kiểm tra. Vui lòng không thanh toán thêm lần nữa và liên hệ nhân viên nếu cần hỗ trợ.";
            case DISPENSING -> "Đang chuẩn bị sản phẩm cho bạn...";
            case COMPLETED -> "Sản phẩm đã được nhả ra. Vui lòng lấy hàng tại ngăn dưới.";
            default -> null;
        };
    }

    private OrderResponseDto mapToOrderResponseDto(Order order,
                                                   OrderResponseDto.OrderPaymentDto paymentDto,
                                                   OrderResponseDto.OrderDispensingDto dispensingDto) {
        OrderResponseDto.OrderProductDto productDto = new OrderResponseDto.OrderProductDto(
                order.getProduct().getId(),
                order.getProduct().getName(),
                order.getUnitPriceVnd()
        );

        return new OrderResponseDto(
                order.getId(),
                order.getPublicOrderCode(),
                order.getMachine().getMachineCode(),
                productDto,
                order.getQuantity(),
                order.getTotalAmountVnd(),
                order.getStatus().name(),
                paymentDto,
                dispensingDto,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
