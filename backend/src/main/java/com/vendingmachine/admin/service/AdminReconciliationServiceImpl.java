package com.vendingmachine.admin.service;

import com.vendingmachine.admin.dto.*;
import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.entity.ReconciliationAuditLog;
import com.vendingmachine.admin.repository.ReconciliationAuditLogRepository;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.device.entity.DeviceEvent;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.entity.DispenseCommand;
import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.payment.entity.PaymentTransaction;
import com.vendingmachine.payment.entity.VerificationStatus;
import com.vendingmachine.payment.repository.PaymentTransactionRepository;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminReconciliationServiceImpl implements AdminReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(AdminReconciliationServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final DispenseCommandRepository dispenseCommandRepository;
    private final DeviceEventRepository deviceEventRepository;
    private final ReconciliationAuditLogRepository reconciliationAuditLogRepository;

    public AdminReconciliationServiceImpl(OrderRepository orderRepository,
                                         ProductRepository productRepository,
                                         PaymentTransactionRepository paymentTransactionRepository,
                                         DispenseCommandRepository dispenseCommandRepository,
                                         DeviceEventRepository deviceEventRepository,
                                         ReconciliationAuditLogRepository reconciliationAuditLogRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.dispenseCommandRepository = dispenseCommandRepository;
        this.deviceEventRepository = deviceEventRepository;
        this.reconciliationAuditLogRepository = reconciliationAuditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReconciliationOrderSummaryDto> getOrdersForReconciliation(OrderStatus status, String orderCode, Pageable pageable) {
        OrderStatus targetStatus = status != null ? status : OrderStatus.NEEDS_RECONCILIATION;

        Page<Order> orders;
        if (orderCode != null && !orderCode.isBlank()) {
            orders = orderRepository.findByStatusAndPublicOrderCodeContainingIgnoreCase(targetStatus, orderCode.trim(), pageable);
        } else {
            orders = orderRepository.findByStatus(targetStatus, pageable);
        }

        return orders.map(this::mapToSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationOrderDetailDto getOrderDetailForReconciliation(UUID orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã đơn hàng không được để trống.", HttpStatus.BAD_REQUEST);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

        ReconciliationOrderDetailDto dto = new ReconciliationOrderDetailDto();
        dto.setOrderId(order.getId());
        dto.setPublicOrderCode(order.getPublicOrderCode());
        dto.setStatus(order.getStatus());
        dto.setTotalAmountVnd(order.getTotalAmountVnd());
        dto.setQuantity(order.getQuantity());
        dto.setTransferReference(order.getTransferReference());
        dto.setPaymentExpiresAt(order.getPaymentExpiresAt());
        dto.setPaidAt(order.getPaidAt());
        dto.setDispensingStartedAt(order.getDispensingStartedAt());
        dto.setCompletedAt(order.getCompletedAt());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setIsReservationHeld(order.isReservationHeld());

        // Machine info
        if (order.getMachine() != null) {
            dto.setMachineId(order.getMachine().getId());
            dto.setMachineCode(order.getMachine().getMachineCode());
            dto.setMachineName(order.getMachine().getName());
        }

        // Product info
        Product product = order.getProduct();
        if (product != null) {
            dto.setProductId(product.getId());
            dto.setProductName(product.getName());
            dto.setSlotNumber(product.getSlotNumber());
            dto.setCurrentStockQuantity(product.getStockQuantity());
            dto.setCurrentReservedQuantity(product.getReservedQuantity());
            dto.setUnitPriceVnd(product.getPriceVnd());
        }

        // 1. Payment transactions
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByOrderId(orderId);
        List<ReconciliationOrderDetailDto.ReconciliationPaymentTxDto> txDtos = new ArrayList<>();
        for (PaymentTransaction tx : transactions) {
            txDtos.add(new ReconciliationOrderDetailDto.ReconciliationPaymentTxDto(
                    tx.getId(),
                    tx.getProviderTransactionId(),
                    tx.getAmountVnd(),
                    tx.getTransferReference(),
                    tx.getVerificationStatus() != null ? tx.getVerificationStatus().name() : null,
                    tx.getTransactionStatus(),
                    tx.getReceivedAt(),
                    tx.getCreatedAt()
            ));
        }
        dto.setPaymentTransactions(txDtos);

        // 2. Dispense commands and device events
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        List<ReconciliationOrderDetailDto.ReconciliationDispenseCmdDto> cmdDtos = new ArrayList<>();
        List<ReconciliationOrderDetailDto.ReconciliationDeviceEventDto> eventDtos = new ArrayList<>();

        for (DispenseCommand cmd : commands) {
            cmdDtos.add(new ReconciliationOrderDetailDto.ReconciliationDispenseCmdDto(
                    cmd.getCommandId(),
                    cmd.getSlotNumber(),
                    cmd.getStatus(),
                    cmd.getAttemptCount(),
                    cmd.getFailureCode(),
                    cmd.getFailureMessage(),
                    cmd.getPublishedAt(),
                    cmd.getAcknowledgedAt(),
                    cmd.getFinishedAt(),
                    cmd.getCreatedAt()
            ));

            List<DeviceEvent> events = deviceEventRepository.findByCommandIdOrderByCreatedAtAsc(cmd.getCommandId());
            for (DeviceEvent evt : events) {
                eventDtos.add(new ReconciliationOrderDetailDto.ReconciliationDeviceEventDto(
                        evt.getEventId(),
                        evt.getCommandId(),
                        evt.getEventType(),
                        evt.getSlotNumber(),
                        evt.getCreatedAt()
                ));
            }
        }
        dto.setDispenseCommands(cmdDtos);
        dto.setDeviceEvents(eventDtos);

        // 3. Reconciliation audit logs
        List<ReconciliationAuditLog> auditLogs = reconciliationAuditLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        List<ReconciliationOrderDetailDto.ReconciliationAuditRecordDto> auditDtos = new ArrayList<>();
        for (ReconciliationAuditLog audit : auditLogs) {
            String adminUser = audit.getAdminUser() != null ? audit.getAdminUser().getUsername() : "SYSTEM";
            auditDtos.add(new ReconciliationOrderDetailDto.ReconciliationAuditRecordDto(
                    audit.getId(),
                    adminUser,
                    audit.getAction(),
                    audit.getPreviousStatus(),
                    audit.getNewStatus(),
                    audit.getReason(),
                    audit.getConfirmationReference(),
                    audit.getCreatedAt()
            ));
        }
        dto.setAuditLogs(auditDtos);

        return dto;
    }

    @Override
    @Transactional
    public ReconciliationDecisionResponseDto recordDecision(UUID orderId, ReconciliationDecisionRequestDto request, AdminUser actor) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã đơn hàng không được để trống.", HttpStatus.BAD_REQUEST);
        }
        if (request == null || request.getDecision() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quyết định đối soát là bắt buộc.", HttpStatus.BAD_REQUEST);
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do đối soát là bắt buộc.", HttpStatus.BAD_REQUEST);
        }

        // Lock order row for update
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

        OrderStatus previousStatus = order.getStatus();

        // Rule: Terminal orders (COMPLETED, CANCELLED) cannot be reconciled
        if (previousStatus == OrderStatus.COMPLETED || previousStatus == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Không thể đối soát đơn hàng đã ở trạng thái kết thúc: " + previousStatus,
                    HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        boolean reservationReleased = false;
        String message;

        switch (request.getDecision()) {
            case REFUND_CONFIRMED -> {
                // Rule: Cannot confirm refund without valid bank confirmation reference proof
                String ref = request.getConfirmationReference();
                if (ref == null || ref.trim().isBlank()) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Quyết định REFUND_CONFIRMED bắt buộc phải có mã tham chiếu xác nhận chuyển khoản hoàn tiền (confirmationReference).",
                            HttpStatus.BAD_REQUEST);
                }

                order.setStatus(OrderStatus.CANCELLED);
                order.setUpdatedAt(now);

                // Inventory safety rule: Only release reservation IF this order actually currently holds it!
                if (order.isReservationHeld()) {
                    if (order.getProduct() != null) {
                        Product product = productRepository.findByIdForUpdate(order.getProduct().getId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm."));

                        int qty = order.getQuantity() != null ? order.getQuantity() : 1;
                        if (product.getReservedQuantity() < qty) {
                            log.error("INVENTORY INCONSISTENCY DETECTED: Order {} holds reservation of {} unit(s), but Product {} has reservedQuantity={}",
                                    order.getId(), qty, product.getId(), product.getReservedQuantity());
                            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK,
                                    "Lỗi bất nhất dữ liệu tồn kho giữ chỗ. Vui lòng kiểm tra tồn kho sản phẩm.");
                        }
                        product.setReservedQuantity(product.getReservedQuantity() - qty);
                        productRepository.save(product);
                        order.setReservationHeld(false);
                        reservationReleased = true;
                        log.info("Released reservation for order {} following confirmed refund (product: {}, newReserved: {})",
                                orderId, product.getName(), product.getReservedQuantity());
                    }
                } else {
                    log.info("Order {} does not hold an active reservation (already released upon expiration or previous failure); skipping reservation decrement",
                            orderId);
                }
                orderRepository.save(order);
                message = reservationReleased
                        ? "Đã xác nhận hoàn tiền và giải phóng số lượng giữ chỗ thành công."
                        : "Đã xác nhận hoàn tiền thành công (đơn hàng trước đó đã được giải phóng giữ chỗ).";
            }
            case REFUND_PENDING -> {
                // Mark pending refund, keep reservation held for safety
                order.setStatus(OrderStatus.NEEDS_RECONCILIATION);
                order.setUpdatedAt(now);
                orderRepository.save(order);
                message = "Đã ghi nhận yêu cầu hoàn tiền đang chờ kế toán/ngân hàng xử lý.";
            }
            case MANUAL_DISPENSE_APPROVED -> {
                // Administrative approval recorded in audit trail; physical stock/reservation held
                order.setStatus(OrderStatus.NEEDS_RECONCILIATION);
                order.setUpdatedAt(now);
                orderRepository.save(order);
                message = "Đã phê duyệt quyết định nhả hàng bù trong hồ sơ đối soát.";
            }
            case INVESTIGATION_NOTE -> {
                order.setUpdatedAt(now);
                orderRepository.save(order);
                message = "Đã ghi chú tiến trình kiểm tra đối soát.";
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quyết định không hợp lệ.", HttpStatus.BAD_REQUEST);
        }

        // Persist audit trail within same atomic transaction
        ReconciliationAuditLog auditLog = new ReconciliationAuditLog();
        auditLog.setOrder(order);
        auditLog.setAdminUser(actor);
        auditLog.setAction(request.getDecision().name());
        auditLog.setPreviousStatus(previousStatus);
        auditLog.setNewStatus(order.getStatus());
        auditLog.setReason(request.getReason().trim());
        auditLog.setConfirmationReference(request.getConfirmationReference() != null ? request.getConfirmationReference().trim() : null);
        auditLog.setCreatedAt(now);
        auditLog = reconciliationAuditLogRepository.save(auditLog);

        log.info("RECONCILIATION_DECISION_RECORDED: orderId={}, decision={}, admin={}, prevStatus={}, newStatus={}, ref={}",
                orderId, request.getDecision(), actor != null ? actor.getUsername() : "UNKNOWN",
                previousStatus, order.getStatus(), request.getConfirmationReference());

        return new ReconciliationDecisionResponseDto(
                auditLog.getId(),
                order.getId(),
                order.getPublicOrderCode(),
                auditLog.getAction(),
                previousStatus,
                order.getStatus(),
                auditLog.getReason(),
                auditLog.getConfirmationReference(),
                reservationReleased,
                now,
                message
        );
    }

    private ReconciliationOrderSummaryDto mapToSummaryDto(Order order) {
        ReconciliationOrderSummaryDto dto = new ReconciliationOrderSummaryDto();
        dto.setOrderId(order.getId());
        dto.setPublicOrderCode(order.getPublicOrderCode());
        dto.setStatus(order.getStatus());
        dto.setTotalAmountVnd(order.getTotalAmountVnd());
        dto.setQuantity(order.getQuantity());
        dto.setTransferReference(order.getTransferReference());
        dto.setPaidAt(order.getPaidAt());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());

        if (order.getMachine() != null) {
            dto.setMachineCode(order.getMachine().getMachineCode());
        }
        if (order.getProduct() != null) {
            dto.setProductName(order.getProduct().getName());
            dto.setSlotNumber(order.getProduct().getSlotNumber());
        }

        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        dto.setHasDispenseCommand(!commands.isEmpty());
        if (!commands.isEmpty()) {
            dto.setLatestDispenseStatus(commands.get(0).getStatus());
        }

        List<PaymentTransaction> txList = paymentTransactionRepository.findByOrderId(order.getId());
        boolean verified = txList.stream().anyMatch(tx -> tx.getVerificationStatus() == VerificationStatus.VERIFIED);
        dto.setPaymentVerified(verified);
        dto.setIsReservationHeld(order.isReservationHeld());

        return dto;
    }
}
