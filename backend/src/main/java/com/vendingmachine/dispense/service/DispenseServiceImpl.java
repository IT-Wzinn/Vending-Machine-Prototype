package com.vendingmachine.dispense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.device.entity.DeviceEvent;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.adapter.DeviceAdapter;
import com.vendingmachine.dispense.adapter.simulated.SimulatedDeviceAdapter;
import com.vendingmachine.dispense.dto.*;
import com.vendingmachine.dispense.entity.DispenseCommand;
import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.repository.MachineRepository;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;

@Service
public class DispenseServiceImpl implements DispenseService {

    private static final Logger log = LoggerFactory.getLogger(DispenseServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MachineRepository machineRepository;
    private final DispenseCommandRepository dispenseCommandRepository;
    private final DeviceEventRepository deviceEventRepository;
    private final DeviceAdapter deviceAdapter;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private ObjectProvider<DispenseService> dispenseServiceProvider;

    @Value("${app.dispense.command-timeout-seconds:45}")
    private int commandTimeoutSeconds;

    @Value("${app.dispense.motor-run-ms:800}")
    private int motorRunMs;

    public DispenseServiceImpl(OrderRepository orderRepository,
                               ProductRepository productRepository,
                               MachineRepository machineRepository,
                               DispenseCommandRepository dispenseCommandRepository,
                               DeviceEventRepository deviceEventRepository,
                               DeviceAdapter deviceAdapter,
                               ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.machineRepository = machineRepository;
        this.dispenseCommandRepository = dispenseCommandRepository;
        this.deviceEventRepository = deviceEventRepository;
        this.deviceAdapter = deviceAdapter;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (deviceAdapter instanceof SimulatedDeviceAdapter simulated) {
            simulated.setEventListener(event -> {
                if (dispenseServiceProvider != null) {
                    dispenseServiceProvider.getObject().handleDeviceEvent(event);
                } else {
                    handleDeviceEvent(event);
                }
            });
            log.info("Registered DispenseService proxy listener to SimulatedDeviceAdapter");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DispenseTriggerResponseDto triggerDispensing(UUID orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OrderId cannot be null", HttpStatus.BAD_REQUEST);
        }

        // Lock order for update to ensure safe state transition
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng."));

        // Rule 1: Only eligible PAID orders may start dispensing
        if (order.getStatus() != OrderStatus.PAID) {
            if (order.getStatus() == OrderStatus.DISPENSING) {
                // Already dispensing - return existing command
                List<DispenseCommand> existing = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
                UUID cmdId = existing.isEmpty() ? null : existing.get(0).getCommandId();
                return new DispenseTriggerResponseDto(orderId, cmdId, "DISPENSING", "Đơn hàng đang trong quá trình xuất hàng.");
            }
            if (order.getStatus() == OrderStatus.COMPLETED) {
                return new DispenseTriggerResponseDto(orderId, null, "COMPLETED", "Đơn hàng đã hoàn tất xuất hàng.");
            }
            throw new BusinessException(ErrorCode.ORDER_NOT_PAID,
                    "Chỉ đơn hàng đã thanh toán mới được phép xuất hàng. Trạng thái hiện tại: " + order.getStatus(),
                    HttpStatus.CONFLICT);
        }

        // Check if there is already an active command for this order
        List<DispenseCommand> existingCommands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        for (DispenseCommand existing : existingCommands) {
            if ("PENDING".equals(existing.getStatus()) || "PUBLISHED".equals(existing.getStatus()) || "ACKNOWLEDGED".equals(existing.getStatus())) {
                log.info("Order {} already has active dispense command {}", orderId, existing.getCommandId());
                return new DispenseTriggerResponseDto(orderId, existing.getCommandId(), "DISPENSING", "Đã có lệnh xuất hàng đang xử lý.");
            }
        }

        Machine machine = order.getMachine();
        Product product = order.getProduct();
        if (machine == null || product == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Dữ liệu đơn hàng thiếu thông tin máy hoặc sản phẩm.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Integer slotNumber = product.getSlotNumber();
        String machineCode = machine.getMachineCode();

        // 1. Transition Order to DISPENSING
        Instant now = Instant.now();
        order.setStatus(OrderStatus.DISPENSING);
        order.setDispensingStartedAt(now);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        // 2. Create and persist DispenseCommand in PENDING state
        UUID commandId = UUID.randomUUID();
        DispenseCommand command = new DispenseCommand();
        command.setCommandId(commandId);
        command.setOrder(order);
        command.setMachine(machine);
        command.setSlotNumber(slotNumber);
        command.setStatus("PENDING");
        command.setAttemptCount(1);
        command.setCreatedAt(now);
        dispenseCommandRepository.save(command);

        log.info("Created DispenseCommand {} for order {}, machine {}, slot {}",
                commandId, orderId, machineCode, slotNumber);

        // 3. Check if device is online before dispatching
        boolean isOnline = deviceAdapter.isDeviceOnline(machineCode);
        if (!isOnline) {
            log.warn("Device {} is OFFLINE. Cannot publish command {}. Transitioning order to NEEDS_RECONCILIATION",
                    machineCode, commandId);
            command.setStatus("FAILED");
            command.setFailureCode("DEVICE_OFFLINE");
            command.setFailureMessage("Device is offline. Safe hold for manual reconciliation.");
            command.setFinishedAt(now);
            dispenseCommandRepository.save(command);

            order.setStatus(OrderStatus.NEEDS_RECONCILIATION);
            order.setUpdatedAt(now);
            orderRepository.save(order);

            return new DispenseTriggerResponseDto(orderId, commandId, "NEEDS_RECONCILIATION",
                    "Thiết bị đang ngoại tuyến. Đơn hàng chuyển sang trạng thái đối soát an toàn.");
        }

        // 4. Publish command via device adapter
        command.setStatus("PUBLISHED");
        command.setPublishedAt(now);
        dispenseCommandRepository.save(command);

        DispenseCommandMessage message = new DispenseCommandMessage(
                commandId,
                machineCode,
                orderId,
                slotNumber,
                now,
                now.plusSeconds(commandTimeoutSeconds),
                Map.of("motorRunMs", motorRunMs)
        );

        // Send via adapter
        deviceAdapter.sendDispenseCommand(message);

        return new DispenseTriggerResponseDto(orderId, commandId, "DISPENSING", "Lệnh xuất hàng đã được phát tới máy.");
    }

    @Override
    @Transactional
    public void handleDeviceEvent(DeviceEventMessage event) {
        if (event == null || event.getEventId() == null || event.getCommandId() == null) {
            log.warn("Received invalid or null device event");
            return;
        }

        UUID eventId = event.getEventId();
        UUID commandId = event.getCommandId();
        String eventType = event.getEventType();

        log.info("Received device event: type={}, eventId={}, commandId={}, machineId={}",
                eventType, eventId, commandId, event.getMachineId());

        // Fast-path / Idempotency check: Has this eventId already been processed and committed?
        if (deviceEventRepository.existsByEventId(eventId)) {
            log.info("Duplicate eventId {} detected in device_events; returning idempotent ACK", eventId);
            // Re-send ACK in case device missed the previous ACK (Crash after commit or network loss)
            sendAckAfterCommit(event);
            return;
        }

        // 1. Persist DeviceEvent into database for durable audit trail
        DeviceEvent deviceEvent = new DeviceEvent();
        deviceEvent.setEventId(eventId);
        deviceEvent.setCommandId(commandId);
        deviceEvent.setEventType(eventType);
        deviceEvent.setSlotNumber(event.getSlotNumber());

        if (event.getMachineId() != null) {
            machineRepository.findByMachineCode(event.getMachineId()).ifPresent(deviceEvent::setMachine);
        }

        try {
            deviceEvent.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            deviceEvent.setPayload(event.toString());
        }
        deviceEvent.setCreatedAt(Instant.now());
        deviceEventRepository.save(deviceEvent);

        // 2. Fetch associated DispenseCommand
        DispenseCommand command = dispenseCommandRepository.findByCommandId(commandId).orElse(null);
        if (command == null) {
            log.warn("Device event {} refers to non-existent commandId {}", eventId, commandId);
            sendAckAfterCommit(event);
            return;
        }

        Order order = command.getOrder();
        if (order == null) {
            log.warn("DispenseCommand {} has no associated order", commandId);
            sendAckAfterCommit(event);
            return;
        }

        // 3. Process event type
        if ("COMMAND_ACCEPTED".equalsIgnoreCase(eventType)) {
            handleCommandAccepted(command, event);
        } else if ("COMMAND_REJECTED".equalsIgnoreCase(eventType)) {
            handleCommandRejected(command, order, event);
        } else if ("DISPENSE_RESULT".equalsIgnoreCase(eventType)) {
            handleDispenseResult(command, order, event);
        } else {
            log.warn("Unknown event type: {}", eventType);
        }

        // 4. Commit-First Policy: Publish EVENT_ACK strictly AFTER database transaction commits
        sendAckAfterCommit(event);
    }

    private void handleCommandAccepted(DispenseCommand command, DeviceEventMessage event) {
        if ("PENDING".equals(command.getStatus()) || "PUBLISHED".equals(command.getStatus())) {
            command.setStatus("ACKNOWLEDGED");
            command.setAcknowledgedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now());
            dispenseCommandRepository.save(command);
            log.info("Dispense command {} marked as ACKNOWLEDGED", command.getCommandId());
        }
    }

    private void handleCommandRejected(DispenseCommand command, Order order, DeviceEventMessage event) {
        String reason = event.getRejectionReason() != null ? event.getRejectionReason() : "UNKNOWN_REJECTION";
        log.warn("Dispense command {} REJECTED by device: {}", command.getCommandId(), reason);

        command.setStatus("REJECTED");
        command.setFailureCode(reason);
        command.setFinishedAt(Instant.now());
        dispenseCommandRepository.save(command);

        // If rejection is DUPLICATE_COMMAND and order is already COMPLETED, do not mutate order
        if ("DUPLICATE_COMMAND".equalsIgnoreCase(reason) && order.getStatus() == OrderStatus.COMPLETED) {
            log.info("Duplicate command rejection for already COMPLETED order {}. No state mutation.", order.getId());
            return;
        }

        // Reject prior to motor execution -> Safe to release reservation, do not deduct stock
        if (order.getStatus() == OrderStatus.DISPENSING || order.getStatus() == OrderStatus.PAID) {
            order.setStatus(OrderStatus.DISPENSE_FAILED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            releaseReservationWithoutStockDeduction(order);
        }
    }

    private void handleDispenseResult(DispenseCommand command, Order order, DeviceEventMessage event) {
        String result = event.getResult();
        Instant now = Instant.now();

        if ("SUCCESS".equalsIgnoreCase(result)) {
            // Idempotent check
            if (order.getStatus() == OrderStatus.COMPLETED) {
                log.info("Order {} is already COMPLETED; skipping duplicate stock deduction", order.getId());
                return;
            }

            // Confirmed successful dispensing:
            // 1. Command status -> COMPLETED
            command.setStatus("COMPLETED");
            command.setFinishedAt(now);
            dispenseCommandRepository.save(command);

            // 2. Inventory consistency rule:
            // Deduct stock_quantity and release reserved_quantity exactly once after confirmed successful dispensing.
            deductStockAndReleaseReservation(order);

            // 3. Order status -> COMPLETED (saved together with reservationHeld status)
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(now);
            order.setUpdatedAt(now);
            orderRepository.save(order);

            log.info("Order {} COMPLETED. Physical stock deducted and reservation cleared. Verification: {}",
                    order.getId(), event.getVerificationLevel());

        } else if ("FAILURE".equalsIgnoreCase(result)) {
            // Confirmed dispensing failure
            if (order.getStatus() == OrderStatus.DISPENSE_FAILED) {
                log.info("Order {} is already DISPENSE_FAILED; skipping duplicate failure processing", order.getId());
                return;
            }

            command.setStatus("FAILED");
            command.setFailureCode(event.getFailureCode());
            command.setFailureMessage(event.getFailureMessage());
            command.setFinishedAt(now);
            dispenseCommandRepository.save(command);

            // Confirmed failure: do NOT deduct stock_quantity. Release reserved_quantity back to available pool.
            releaseReservationWithoutStockDeduction(order);

            order.setStatus(OrderStatus.DISPENSE_FAILED);
            order.setUpdatedAt(now);
            orderRepository.save(order);

            log.warn("Order {} marked as DISPENSE_FAILED. FailureCode: {}, Reason: {}. Reservation released.",
                    order.getId(), event.getFailureCode(), event.getFailureMessage());

        } else if ("UNCERTAIN".equalsIgnoreCase(result)) {
            // Uncertain outcome (e.g. reboot during motor run)
            order.setStatus(OrderStatus.NEEDS_RECONCILIATION);
            order.setUpdatedAt(now);
            orderRepository.save(order);

            command.setStatus("UNCERTAIN");
            command.setFailureCode(event.getFailureCode());
            command.setFailureMessage(event.getFailureMessage());
            command.setFinishedAt(now);
            dispenseCommandRepository.save(command);

            // Stock and reservation are PRESERVED as-is for safe manual reconciliation.
            // Never automatically retry or deduct stock when outcome is uncertain!
            log.warn("Order {} marked as NEEDS_RECONCILIATION. Stock and reservation preserved for manual audit.",
                    order.getId());
        }
    }

    private void deductStockAndReleaseReservation(Order order) {
        if (order.getProduct() == null) return;
        UUID productId = order.getProduct().getId();
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseGet(() -> productRepository.findById(productId).orElse(null));
        if (product != null) {
            int qty = order.getQuantity() != null ? order.getQuantity() : 1;
            
            // 1. Release reservation if held
            if (order.isReservationHeld()) {
                if (product.getReservedQuantity() >= qty) {
                    product.setReservedQuantity(product.getReservedQuantity() - qty);
                } else {
                    log.error("INVENTORY INCONSISTENCY: Product {} has reservedQuantity={} less than order quantity {}",
                            product.getId(), product.getReservedQuantity(), qty);
                    product.setReservedQuantity(0);
                }
                order.setReservationHeld(false);
            }

            // 2. Deduct physical stock
            if (product.getStockQuantity() >= qty) {
                product.setStockQuantity(product.getStockQuantity() - qty);
            } else {
                log.error("INVENTORY INCONSISTENCY: Product {} has stockQuantity={} less than order quantity {}",
                        product.getId(), product.getStockQuantity(), qty);
                product.setStockQuantity(0);
            }

            productRepository.save(product);
            log.info("Product {} ({}) updated: stockQuantity={}, reservedQuantity={}",
                    product.getId(), product.getName(), product.getStockQuantity(), product.getReservedQuantity());
        }
    }

    private void releaseReservationWithoutStockDeduction(Order order) {
        if (order.getProduct() == null) return;
        UUID productId = order.getProduct().getId();
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseGet(() -> productRepository.findById(productId).orElse(null));
        if (product != null) {
            int qty = order.getQuantity() != null ? order.getQuantity() : 1;
            if (order.isReservationHeld()) {
                if (product.getReservedQuantity() >= qty) {
                    product.setReservedQuantity(product.getReservedQuantity() - qty);
                } else {
                    log.error("INVENTORY INCONSISTENCY: Product {} has reservedQuantity={} less than order quantity {}",
                            product.getId(), product.getReservedQuantity(), qty);
                    product.setReservedQuantity(0);
                }
                order.setReservationHeld(false);
                productRepository.save(product);
                log.info("Released reservation for order {} without stock deduction (newReserved: {}, stockQuantity: {})",
                        order.getId(), product.getReservedQuantity(), product.getStockQuantity());
            } else {
                log.info("Order {} does not hold an active reservation; skipping release", order.getId());
            }
        }
    }

    private void sendAckAfterCommit(DeviceEventMessage event) {
        EventAckMessage ack = new EventAckMessage(
                UUID.randomUUID(),
                event.getEventId(),
                event.getCommandId(),
                event.getMachineId(),
                event.getOrderId(),
                "PROCESSED",
                Instant.now()
        );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deviceAdapter.sendEventAck(ack);
                }
            });
        } else {
            deviceAdapter.sendEventAck(ack);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DispenseCommandResponseDto getDispenseCommandByOrderId(UUID orderId) {
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (commands.isEmpty()) {
            throw new ResourceNotFoundException(ErrorCode.DISPENSE_COMMAND_NOT_FOUND, "Không tìm thấy lệnh xuất hàng cho đơn này.");
        }
        DispenseCommand cmd = commands.get(0);
        DispenseCommandResponseDto dto = new DispenseCommandResponseDto();
        dto.setCommandId(cmd.getCommandId());
        dto.setOrderId(orderId);
        dto.setMachineCode(cmd.getMachine() != null ? cmd.getMachine().getMachineCode() : null);
        dto.setSlotNumber(cmd.getSlotNumber());
        dto.setStatus(cmd.getStatus());
        dto.setAttemptCount(cmd.getAttemptCount());
        dto.setFailureCode(cmd.getFailureCode());
        dto.setFailureMessage(cmd.getFailureMessage());
        dto.setPublishedAt(cmd.getPublishedAt());
        dto.setAcknowledgedAt(cmd.getAcknowledgedAt());
        dto.setFinishedAt(cmd.getFinishedAt());
        dto.setCreatedAt(cmd.getCreatedAt());
        return dto;
    }

    @Override
    @Scheduled(fixedDelayString = "${app.dispense.timeout-check-interval-ms:5000}")
    @Transactional
    public int checkAndHandleTimeouts() {
        Instant cutoff = Instant.now().minusSeconds(commandTimeoutSeconds);
        List<DispenseCommand> timedOutCommands = dispenseCommandRepository.findTimedOutCommands(
                List.of("PENDING", "PUBLISHED", "ACKNOWLEDGED"), cutoff);

        if (timedOutCommands.isEmpty()) {
            return 0;
        }

        int count = 0;
        Instant now = Instant.now();
        for (DispenseCommand cmd : timedOutCommands) {
            log.warn("DispenseCommand {} timed out after {} seconds. Transitioning to TIMEOUT / NEEDS_RECONCILIATION",
                    cmd.getCommandId(), commandTimeoutSeconds);

            cmd.setStatus("TIMEOUT");
            cmd.setFailureCode("TIMEOUT");
            cmd.setFailureMessage("No response from device within timeout limit of " + commandTimeoutSeconds + "s.");
            cmd.setFinishedAt(now);
            dispenseCommandRepository.save(cmd);

            Order order = cmd.getOrder();
            if (order != null && order.getStatus() == OrderStatus.DISPENSING) {
                // Rule: Timeout is NOT proof of failure. Transition to NEEDS_RECONCILIATION.
                order.setStatus(OrderStatus.NEEDS_RECONCILIATION);
                order.setUpdatedAt(now);
                orderRepository.save(order);
                // Preserve stock and reservation for manual audit!
            }
            count++;
        }

        if (count > 0) {
            log.info("Handled {} timed-out dispense commands", count);
        }
        return count;
    }
}
