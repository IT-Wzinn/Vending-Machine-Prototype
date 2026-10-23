package com.vendingmachine.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.payment.dto.SePayWebhookDto;
import com.vendingmachine.payment.dto.SePayWebhookResponseDto;
import com.vendingmachine.payment.entity.PaymentTransaction;
import com.vendingmachine.payment.entity.VerificationStatus;
import com.vendingmachine.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final Pattern REF_PATTERN = Pattern.compile("(?i)(VM[A-Z0-9_-]+)");
    private static final DateTimeFormatter SEPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VietQrService vietQrService;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.demo-mode:false}")
    private boolean demoMode;

    @Value("${app.payment.sepay.api-key:}")
    private String configuredApiKey;

    @Value("${app.payment.sepay.bank-bin:970422}")
    private String bankBin;

    @Value("${app.payment.sepay.bank-name:MBBank}")
    private String bankName;

    @Value("${app.payment.sepay.account-number:0345678999}")
    private String accountNumber;

    @Value("${app.payment.sepay.account-name:SMART VENDING MACHINE}")
    private String accountName;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    @Lazy
    private com.vendingmachine.dispense.service.DispenseService dispenseService;

    @Value("${app.dispense.auto-trigger:true}")
    private boolean autoTriggerDispense = true;

    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentTransactionRepository paymentTransactionRepository,
                              VietQrService vietQrService,
                              ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.vietQrService = vietQrService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (this.transactionTemplate == null && this.transactionManager != null) {
            this.transactionTemplate = new TransactionTemplate(this.transactionManager);
        }
    }

    @Override
    public OrderResponseDto.OrderPaymentDto createPaymentInstructions(Order order) {
        String qrData = vietQrService.generateVietQr(
                bankBin,
                accountNumber,
                order.getTotalAmountVnd(),
                order.getTransferReference()
        );

        return new OrderResponseDto.OrderPaymentDto(
                order.getTransferReference(),
                qrData,
                accountNumber,
                accountName,
                bankName,
                order.getTotalAmountVnd(),
                order.getPaymentExpiresAt(),
                order.getPaidAt()
        );
    }

    @Override
    public SePayWebhookResponseDto processSePayWebhook(String authHeader, SePayWebhookDto payload) {
        // 1. Verify Webhook Authentication
        authenticateWebhook(authHeader);

        if (payload == null || payload.getId() == null) {
            log.warn("Received empty or invalid SePay webhook payload");
            return SePayWebhookResponseDto.ok("Payload missing id, ignored");
        }

        String providerTxId = String.valueOf(payload.getId());

        // 2. Fast-path check: prevent duplicate processing if already persisted
        if (paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", providerTxId)) {
            log.info("Duplicate SePay transaction ID {} received; returning idempotent success", providerTxId);
            return SePayWebhookResponseDto.ok("Duplicate transaction ignored");
        }

        // 3. Filter out non-incoming transactions
        if (payload.getTransferType() != null && !payload.getTransferType().equalsIgnoreCase("in")) {
            log.info("Ignored non-incoming transaction ID {} (type: {})", providerTxId, payload.getTransferType());
            return SePayWebhookResponseDto.ok("Non-incoming transaction ignored");
        }

        // Mutual exclusion per provider transaction ID to prevent concurrent duplicates from colliding
        synchronized (("SEPAY_TX_" + providerTxId).intern()) {
            if (paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", providerTxId)) {
                log.info("Duplicate SePay transaction ID {} verified in sync block; returning idempotent success", providerTxId);
                return SePayWebhookResponseDto.ok("Duplicate transaction ignored");
            }

            try {
                if (transactionTemplate != null) {
                    return transactionTemplate.execute(status -> doProcessSePayWebhook(payload, providerTxId));
                } else {
                    return doProcessSePayWebhook(payload, providerTxId);
                }
            } catch (DataIntegrityViolationException ex) {
                log.info("Duplicate SePay transaction ID {} caught DataIntegrityViolationException; returning idempotent success", providerTxId);
                return SePayWebhookResponseDto.ok("Duplicate transaction ignored");
            }
        }
    }

    @Transactional
    public SePayWebhookResponseDto doProcessSePayWebhook(SePayWebhookDto payload, String providerTxId) {
        String rawPayloadJson = serializeRawPayload(payload);
        Instant receivedAt = parseTransactionDate(payload.getTransactionDate());
        int receivedAmount = payload.getTransferAmount() != null ? payload.getTransferAmount() : 0;

        // 4. Extract transfer reference from code or content
        String transferRef = extractTransferReference(payload);

        if (transferRef == null) {
            // Unmatched reference
            saveTransaction(providerTxId, null, receivedAmount, null, receivedAt,
                    rawPayloadJson, VerificationStatus.UNMATCHED, "SUCCESS");
            log.warn("SePay transaction {} has no identifiable transfer reference in content: '{}'",
                    providerTxId, payload.getContent());
            return SePayWebhookResponseDto.ok("Unmatched transfer reference recorded");
        }

        // 5. Match Order with Pessimistic Lock
        Optional<Order> orderOpt;
        try {
            orderOpt = orderRepository.findByTransferReferenceForUpdate(transferRef);
        } catch (Exception e) {
            orderOpt = orderRepository.findByTransferReference(transferRef);
        }

        if (orderOpt.isEmpty()) {
            orderOpt = orderRepository.findByTransferReference(transferRef);
        }

        if (orderOpt.isEmpty()) {
            saveTransaction(providerTxId, null, receivedAmount, transferRef, receivedAt,
                    rawPayloadJson, VerificationStatus.UNMATCHED, "SUCCESS");
            log.warn("SePay transaction {} reference '{}' does not match any existing order",
                    providerTxId, transferRef);
            return SePayWebhookResponseDto.ok("Order not found, recorded as unmatched");
        }

        Order order = orderOpt.get();

        // 6. Check amount
        if (receivedAmount < order.getTotalAmountVnd()) {
            saveTransaction(providerTxId, order, receivedAmount, transferRef, receivedAt,
                    rawPayloadJson, VerificationStatus.UNDERPAID, "UNDERPAID");
            log.warn("Underpayment for order {}: received {} VND, expected {} VND",
                    order.getId(), receivedAmount, order.getTotalAmountVnd());
            return SePayWebhookResponseDto.ok("Underpayment recorded");
        }

        // 7. Check current order status
        if (order.getStatus() == OrderStatus.PAID) {
            // Already paid: customer transferred multiple times
            saveTransaction(providerTxId, order, receivedAmount, transferRef, receivedAt,
                    rawPayloadJson, VerificationStatus.DUPLICATE, "SUCCESS");
            log.warn("Multiple payments for already PAID order {}: recorded transaction {}",
                    order.getId(), providerTxId);
            return SePayWebhookResponseDto.ok("Duplicate payment for paid order recorded");
        }

        if (order.getStatus() == OrderStatus.EXPIRED || Instant.now().isAfter(order.getPaymentExpiresAt())) {
            // Late payment: order expired before payment arrived
            saveTransaction(providerTxId, order, receivedAmount, transferRef, receivedAt,
                    rawPayloadJson, VerificationStatus.LATE_PAYMENT, "LATE_PAYMENT");
            // Mark order for reconciliation so operator can inspect and refund/remedy
            orderRepository.updateOrderStatusIfExpected(order.getId(), OrderStatus.EXPIRED, OrderStatus.NEEDS_RECONCILIATION, Instant.now());
            log.warn("Late payment received for EXPIRED order {}. Marked as NEEDS_RECONCILIATION", order.getId());
            return SePayWebhookResponseDto.ok("Late payment recorded for reconciliation");
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            // 8. Atomic transition to PAID
            Instant now = Instant.now();
            int updatedRows = orderRepository.markOrderAsPaidIfPending(order.getId(), now, now);

            if (updatedRows == 1) {
                // Stock reservation is KEPT in Phase 5 per specification (stock_quantity is not deducted here)
                saveTransaction(providerTxId, order, receivedAmount, transferRef, receivedAt,
                        rawPayloadJson, VerificationStatus.VERIFIED, "SUCCESS");
                log.info("Order {} successfully verified and marked as PAID (Tx: {}, Amount: {} VND)",
                        order.getId(), providerTxId, receivedAmount);

                if (autoTriggerDispense && dispenseService != null) {
                    if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                                new org.springframework.transaction.support.TransactionSynchronization() {
                                    @Override
                                    public void afterCommit() {
                                        try {
                                            dispenseService.triggerDispensing(order.getId());
                                        } catch (Exception e) {
                                            log.error("Failed to auto-trigger dispensing for order {}", order.getId(), e);
                                        }
                                    }
                                });
                    } else {
                        try {
                            dispenseService.triggerDispensing(order.getId());
                        } catch (Exception e) {
                            log.error("Failed to auto-trigger dispensing for order {}", order.getId(), e);
                        }
                    }
                }

                return SePayWebhookResponseDto.ok("Payment verified and order marked as PAID");
            } else {
                // Another thread transitioned order in between (e.g. concurrent expiration or duplicate webhook)
                Order freshOrder = orderRepository.findById(order.getId()).orElse(order);
                if (freshOrder.getStatus() == OrderStatus.EXPIRED) {
                    saveTransaction(providerTxId, freshOrder, receivedAmount, transferRef, receivedAt,
                            rawPayloadJson, VerificationStatus.LATE_PAYMENT, "LATE_PAYMENT");
                    orderRepository.updateOrderStatusIfExpected(freshOrder.getId(), OrderStatus.EXPIRED, OrderStatus.NEEDS_RECONCILIATION, now);
                    return SePayWebhookResponseDto.ok("Late payment recorded");
                } else {
                    saveTransaction(providerTxId, freshOrder, receivedAmount, transferRef, receivedAt,
                            rawPayloadJson, VerificationStatus.DUPLICATE, "SUCCESS");
                    return SePayWebhookResponseDto.ok("Concurrent duplicate payment recorded");
                }
            }
        }

        // Catch-all for other states (e.g. CANCELLED, NEEDS_RECONCILIATION)
        saveTransaction(providerTxId, order, receivedAmount, transferRef, receivedAt,
                rawPayloadJson, VerificationStatus.UNMATCHED, "IRREGULAR");
        return SePayWebhookResponseDto.ok("Transaction recorded for irregular order status");
    }

    private void authenticateWebhook(String authHeader) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            if (demoMode) {
                log.warn("SePay API key not configured; accepting webhook in DEMO mode");
                return;
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Webhook API key not configured on server", HttpStatus.UNAUTHORIZED);
        }

        if (authHeader == null || authHeader.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing Authorization header in webhook request", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.trim();
        if (token.startsWith("Apikey ")) {
            token = token.substring(7).trim();
        } else if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        if (!configuredApiKey.equals(token)) {
            log.warn("Invalid webhook credentials received");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid SePay webhook API key", HttpStatus.UNAUTHORIZED);
        }
    }

    private String extractTransferReference(SePayWebhookDto payload) {
        if (payload.getCode() != null && !payload.getCode().isBlank()) {
            return payload.getCode().trim();
        }

        String content = payload.getContent();
        if (content != null && !content.isBlank()) {
            Matcher matcher = REF_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        String description = payload.getDescription();
        if (description != null && !description.isBlank()) {
            Matcher matcher = REF_PATTERN.matcher(description);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private void saveTransaction(String providerTxId, Order order, int amountVnd, String transferRef,
                                 Instant receivedAt, String rawPayload, VerificationStatus status, String txStatus) {
        try {
            PaymentTransaction tx = new PaymentTransaction(
                    UUID.randomUUID(),
                    "SEPAY",
                    providerTxId,
                    order,
                    amountVnd,
                    "VND",
                    "INCOMING",
                    txStatus,
                    transferRef,
                    receivedAt,
                    Instant.now(),
                    rawPayload,
                    status
            );
            paymentTransactionRepository.saveAndFlush(tx);
        } catch (DataIntegrityViolationException ex) {
            log.info("Payment transaction {} already persisted concurrently", providerTxId);
        }
    }

    private Instant parseTransactionDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return Instant.now();
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(dateStr, SEPAY_DATE_FORMAT);
            return ldt.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String serializeRawPayload(SePayWebhookDto payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
