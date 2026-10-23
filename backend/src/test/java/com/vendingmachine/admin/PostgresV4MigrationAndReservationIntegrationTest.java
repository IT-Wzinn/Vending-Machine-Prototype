package com.vendingmachine.admin;

import com.vendingmachine.admin.dto.ReconciliationDecisionRequestDto;
import com.vendingmachine.admin.dto.ReconciliationDecisionRequestDto.ReconciliationDecision;
import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.repository.AdminUserRepository;
import com.vendingmachine.admin.repository.ReconciliationAuditLogRepository;
import com.vendingmachine.admin.security.AdminPasswordService;
import com.vendingmachine.admin.service.AdminReconciliationService;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.repository.DispenseCommandRepository;
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
import com.vendingmachine.payment.entity.PaymentTransaction;
import com.vendingmachine.payment.entity.VerificationStatus;
import com.vendingmachine.payment.repository.PaymentTransactionRepository;
import com.vendingmachine.payment.service.PaymentService;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated PostgreSQL Integration Tests for Migration V4 and Reservation Lifecycle.
 * Runs against schema test_pg_concurrency on the real PostgreSQL 18 engine,
 * guaranteeing zero mutation of public dev schema data.
 */
@SpringBootTest
@ActiveProfiles("test-postgres")
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class PostgresV4MigrationAndReservationIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AdminReconciliationService adminReconciliationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private DispenseCommandRepository dispenseCommandRepository;

    @Autowired
    private DeviceEventRepository deviceEventRepository;

    @Autowired
    private ReconciliationAuditLogRepository reconciliationAuditLogRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminPasswordService adminPasswordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.payment.sepay.api-key:test-sepay-secret-key}")
    private String configuredApiKey;

    private Machine testMachine;
    private Product testProduct;
    private AdminUser testAdmin;

    @BeforeEach
    void setUp() {
        cleanData();

        testMachine = new Machine(
                UUID.randomUUID(),
                "VM_PG_V4_TEST",
                "Máy kiểm thử PostgreSQL V4",
                MachineStatus.ACTIVE,
                null
        );
        testMachine = machineRepository.save(testMachine);

        testProduct = new Product(
                UUID.randomUUID(),
                testMachine,
                "Trà Xanh C2 500ml",
                "Nước giải khát",
                "https://example.com/c2.jpg",
                10000,
                1,
                10,
                0,
                true
        );
        testProduct = productRepository.save(testProduct);

        testAdmin = new AdminUser();
        testAdmin.setId(UUID.randomUUID());
        testAdmin.setUsername("pg_v4_admin_" + UUID.randomUUID().toString().substring(0, 8));
        testAdmin.setPasswordHash(adminPasswordService.hashPassword("SecureAdminPass2026!"));
        testAdmin.setTokenVersion(1);
        testAdmin.setCreatedAt(Instant.now());
        testAdmin.setUpdatedAt(Instant.now());
        testAdmin = adminUserRepository.save(testAdmin);
    }

    @AfterEach
    void tearDown() {
        cleanData();
    }

    private void cleanData() {
        reconciliationAuditLogRepository.deleteAll();
        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();
        adminUserRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Đơn EXPIRED đã nhả reservation: is_reservation_held = false và reserved_quantity = 0 trên PostgreSQL")
    void expiredOrder_releasesReservationAndSetsReservationHeldFalse() {
        CreateOrderRequestDto req = new CreateOrderRequestDto(testMachine.getMachineCode(), testProduct.getId(), 1);
        OrderResponseDto dto = orderService.createOrder(req);

        Product prodAfterCreate = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterCreate.getReservedQuantity()).isEqualTo(1);

        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow();
        assertThat(order.isReservationHeld()).isTrue();

        // Expire order
        boolean expired = orderService.expireOrderIfPending(order.getId());
        assertThat(expired).isTrue();

        Order orderAfter = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(orderAfter.isReservationHeld()).isFalse();

        Product prodAfterExpire = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterExpire.getReservedQuantity()).isEqualTo(0);
        assertThat(prodAfterExpire.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("2. Đơn thanh toán muộn chuyển sang NEEDS_RECONCILIATION nhưng không còn reservation")
    void latePaidOrder_transitionsToNeedsReconciliation_withoutReReservingStock() {
        CreateOrderRequestDto req = new CreateOrderRequestDto(testMachine.getMachineCode(), testProduct.getId(), 1);
        OrderResponseDto dto = orderService.createOrder(req);
        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow();

        // Order expires first
        orderService.expireOrderIfPending(dto.getOrderId());

        Product prodAfterExpire = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterExpire.getReservedQuantity()).isEqualTo(0);

        // Late payment webhook arrives
        SePayWebhookDto payload = new SePayWebhookDto();
        long providerTxId = 88001L;
        payload.setId(providerTxId);
        payload.setTransferType("in");
        payload.setTransferAmount(10000);
        payload.setCode(order.getTransferReference());

        paymentService.processSePayWebhook("Apikey " + configuredApiKey, payload);

        Order orderAfterPayment = orderRepository.findById(dto.getOrderId()).orElseThrow();
        assertThat(orderAfterPayment.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        assertThat(orderAfterPayment.isReservationHeld()).isFalse();

        PaymentTransaction tx = paymentTransactionRepository.findByProviderAndProviderTransactionId("SEPAY", String.valueOf(providerTxId)).orElseThrow();
        assertThat(tx.getVerificationStatus()).isEqualTo(VerificationStatus.LATE_PAYMENT);

        // Reservation remains 0 on PostgreSQL
        Product prodFinal = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodFinal.getReservedQuantity()).isEqualTo(0);
        assertThat(prodFinal.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("3. Đơn NEEDS_RECONCILIATION do lỗi nhả hàng / stale paid vẫn đang giữ reservation")
    void stalePaidOrOfflineOrder_inNeedsReconciliation_preservesReservationHeldTrue() {
        CreateOrderRequestDto req = new CreateOrderRequestDto(testMachine.getMachineCode(), testProduct.getId(), 1);
        OrderResponseDto dto = orderService.createOrder(req);
        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow();

        // Pay on time -> PAID
        SePayWebhookDto payload = new SePayWebhookDto();
        long providerTxId = 88002L;
        payload.setId(providerTxId);
        payload.setTransferType("in");
        payload.setTransferAmount(10000);
        payload.setCode(order.getTransferReference());

        paymentService.processSePayWebhook("Apikey " + configuredApiKey, payload);

        // Simulate transition to NEEDS_RECONCILIATION (e.g. stale paid poller or offline device)
        Order paidOrder = orderRepository.findById(dto.getOrderId()).orElseThrow();
        paidOrder.setStatus(OrderStatus.NEEDS_RECONCILIATION);
        orderRepository.save(paidOrder);

        Order reconciledOrder = orderRepository.findById(dto.getOrderId()).orElseThrow();
        assertThat(reconciledOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        assertThat(reconciledOrder.isReservationHeld()).isTrue();

        Product prodFinal = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodFinal.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("4. Hai đơn cùng sản phẩm: Đơn A (thanh toán muộn, không giữ reservation) và Đơn B (đang giữ reservation)")
    void twoOrdersSameProduct_oneLatePaidAndOneActive_onlyActiveHoldsReservation() {
        // Order A: expired, then late paid -> NEEDS_RECONCILIATION (reservationHeld = false)
        CreateOrderRequestDto reqA = new CreateOrderRequestDto(testMachine.getMachineCode(), testProduct.getId(), 1);
        OrderResponseDto dtoA = orderService.createOrder(reqA);
        Order orderAInitial = orderRepository.findById(dtoA.getOrderId()).orElseThrow();
        orderService.expireOrderIfPending(dtoA.getOrderId());

        SePayWebhookDto payloadA = new SePayWebhookDto();
        payloadA.setId(88003L);
        payloadA.setTransferType("in");
        payloadA.setTransferAmount(10000);
        payloadA.setCode(orderAInitial.getTransferReference());
        paymentService.processSePayWebhook("Apikey " + configuredApiKey, payloadA);

        Order orderA = orderRepository.findById(dtoA.getOrderId()).orElseThrow();
        assertThat(orderA.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        assertThat(orderA.isReservationHeld()).isFalse();

        // Order B: active new order -> PENDING_PAYMENT (reservationHeld = true)
        CreateOrderRequestDto reqB = new CreateOrderRequestDto(testMachine.getMachineCode(), testProduct.getId(), 1);
        OrderResponseDto dtoB = orderService.createOrder(reqB);

        Order orderB = orderRepository.findById(dtoB.getOrderId()).orElseThrow();
        assertThat(orderB.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderB.isReservationHeld()).isTrue();

        // Product reservedQuantity must be exactly 1 (held by Order B only!)
        Product product = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(product.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("5. Hoàn tiền cho Đơn A (không giữ reservation) KHÔNG giải phóng reservation của Đơn B")
    void refundLatePaidOrder_doesNotStealReservationFromOtherOrder() {
        // 1. Setup Order A: NEEDS_RECONCILIATION, reservationHeld = false
        CreateOrderRequestDto reqA = new CreateOrderRequestDto(testMachine.getMachineCode(), testProduct.getId(), 1);
        OrderResponseDto dtoA = orderService.createOrder(reqA);
        Order orderAInitial = orderRepository.findById(dtoA.getOrderId()).orElseThrow();
        orderService.expireOrderIfPending(dtoA.getOrderId());

        SePayWebhookDto payloadA = new SePayWebhookDto();
        payloadA.setId(88004L);
        payloadA.setTransferType("in");
        payloadA.setTransferAmount(10000);
        payloadA.setCode(orderAInitial.getTransferReference());
        paymentService.processSePayWebhook("Apikey " + configuredApiKey, payloadA);

        // 2. Setup Order B: Active order holding reservation
        CreateOrderRequestDto reqB = new CreateOrderRequestDto(testMachine.getMachineCode(), testProduct.getId(), 1);
        OrderResponseDto dtoB = orderService.createOrder(reqB);

        Product prodBeforeRefund = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodBeforeRefund.getReservedQuantity()).isEqualTo(1);
        assertThat(prodBeforeRefund.getStockQuantity()).isEqualTo(10);

        // 3. Admin confirms refund for Order A
        ReconciliationDecisionRequestDto decisionReqA = new ReconciliationDecisionRequestDto(
                ReconciliationDecision.REFUND_CONFIRMED,
                "Hoàn tiền do khách chuyển khoản sau khi đơn hết hạn",
                "BANK-REFUND-TX-001"
        );
        adminReconciliationService.recordDecision(dtoA.getOrderId(), decisionReqA, testAdmin);

        // 4. Verify Order A is CANCELLED and reservationHeld is false
        Order orderAfterRefundA = orderRepository.findById(dtoA.getOrderId()).orElseThrow();
        assertThat(orderAfterRefundA.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderAfterRefundA.isReservationHeld()).isFalse();

        // 5. CRITICAL INVENTORY INVARIANT: Product reservedQuantity MUST STILL BE 1 for Order B!
        Product prodAfterRefundA = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterRefundA.getReservedQuantity()).isEqualTo(1);
        assertThat(prodAfterRefundA.getStockQuantity()).isEqualTo(10);

        // 6. Now confirm refund for Order B (which actually holds reservation)
        // Transition Order B to NEEDS_RECONCILIATION first
        Order orderB = orderRepository.findById(dtoB.getOrderId()).orElseThrow();
        orderB.setStatus(OrderStatus.NEEDS_RECONCILIATION);
        orderRepository.save(orderB);

        ReconciliationDecisionRequestDto decisionReqB = new ReconciliationDecisionRequestDto(
                ReconciliationDecision.REFUND_CONFIRMED,
                "Hoàn tiền cho đơn B",
                "BANK-REFUND-TX-002"
        );
        adminReconciliationService.recordDecision(dtoB.getOrderId(), decisionReqB, testAdmin);

        Order orderAfterRefundB = orderRepository.findById(dtoB.getOrderId()).orElseThrow();
        assertThat(orderAfterRefundB.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderAfterRefundB.isReservationHeld()).isFalse();

        // 7. Now Product reservedQuantity drops from 1 to 0, stock remains 10
        Product prodAfterRefundB = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterRefundB.getReservedQuantity()).isEqualTo(0);
        assertThat(prodAfterRefundB.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("6. Thực thi trực tiếp các câu lệnh SQL Backfill của V4 trên PostgreSQL thật")
    void backfillV4_rawSqlDirectlyExecuted_accuratelySetsReservationHeldOnHistoricalData() {
        // Create 4 distinct historical order scenarios directly via SQL
        UUID idExpired = UUID.randomUUID();
        UUID idCompleted = UUID.randomUUID();
        UUID idLateRecon = UUID.randomUUID();
        UUID idStaleRecon = UUID.randomUUID();

        // Seed orders with is_reservation_held = true (default initial state)
        jdbcTemplate.update(
                "INSERT INTO orders (id, machine_id, product_id, public_order_code, transfer_reference, quantity, unit_price_vnd, total_amount_vnd, status, is_reservation_held, payment_expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, 1, 10000, 10000, 'EXPIRED', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                idExpired, testMachine.getId(), testProduct.getId(), "VM_V4_EXP", "REF_EXP"
        );
        jdbcTemplate.update(
                "INSERT INTO orders (id, machine_id, product_id, public_order_code, transfer_reference, quantity, unit_price_vnd, total_amount_vnd, status, is_reservation_held, payment_expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, 1, 10000, 10000, 'COMPLETED', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                idCompleted, testMachine.getId(), testProduct.getId(), "VM_V4_COM", "REF_COM"
        );
        jdbcTemplate.update(
                "INSERT INTO orders (id, machine_id, product_id, public_order_code, transfer_reference, quantity, unit_price_vnd, total_amount_vnd, status, is_reservation_held, payment_expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, 1, 10000, 10000, 'NEEDS_RECONCILIATION', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                idLateRecon, testMachine.getId(), testProduct.getId(), "VM_V4_LAT", "REF_LAT"
        );
        jdbcTemplate.update(
                "INSERT INTO orders (id, machine_id, product_id, public_order_code, transfer_reference, quantity, unit_price_vnd, total_amount_vnd, status, is_reservation_held, payment_expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, 1, 10000, 10000, 'NEEDS_RECONCILIATION', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                idStaleRecon, testMachine.getId(), testProduct.getId(), "VM_V4_STA", "REF_STA"
        );

        // Add LATE_PAYMENT transaction for idLateRecon
        jdbcTemplate.update(
                "INSERT INTO payment_transactions (id, order_id, provider, provider_transaction_id, amount_vnd, transfer_reference, verification_status, transaction_status, received_at, raw_payload, created_at) " +
                        "VALUES (?, ?, 'SEPAY', 'TX_LATE', 10000, 'REF_LAT', 'LATE_PAYMENT', 'LATE_PAYMENT', CURRENT_TIMESTAMP, '{}', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), idLateRecon
        );

        // Add VERIFIED transaction for idStaleRecon
        jdbcTemplate.update(
                "INSERT INTO payment_transactions (id, order_id, provider, provider_transaction_id, amount_vnd, transfer_reference, verification_status, transaction_status, received_at, raw_payload, created_at) " +
                        "VALUES (?, ?, 'SEPAY', 'TX_VERI', 10000, 'REF_STA', 'VERIFIED', 'SUCCESS', CURRENT_TIMESTAMP, '{}', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), idStaleRecon
        );

        // Now execute the EXACT Step 2 and Step 3 from V4 migration:
        // Step 2: Terminal orders backfill
        jdbcTemplate.update(
                "UPDATE orders SET is_reservation_held = false WHERE status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'DISPENSE_FAILED')"
        );

        // Step 3: Late-paid NEEDS_RECONCILIATION backfill
        jdbcTemplate.update(
                "UPDATE orders o SET is_reservation_held = false WHERE o.status = 'NEEDS_RECONCILIATION' AND EXISTS (" +
                        "SELECT 1 FROM payment_transactions pt WHERE pt.order_id = o.id AND pt.verification_status = 'LATE_PAYMENT')"
        );

        // Assert results in PostgreSQL database
        Boolean heldExpired = jdbcTemplate.queryForObject("SELECT is_reservation_held FROM orders WHERE id = ?", Boolean.class, idExpired);
        Boolean heldCompleted = jdbcTemplate.queryForObject("SELECT is_reservation_held FROM orders WHERE id = ?", Boolean.class, idCompleted);
        Boolean heldLateRecon = jdbcTemplate.queryForObject("SELECT is_reservation_held FROM orders WHERE id = ?", Boolean.class, idLateRecon);
        Boolean heldStaleRecon = jdbcTemplate.queryForObject("SELECT is_reservation_held FROM orders WHERE id = ?", Boolean.class, idStaleRecon);

        assertThat(heldExpired).isFalse();
        assertThat(heldCompleted).isFalse();
        assertThat(heldLateRecon).isFalse();
        assertThat(heldStaleRecon).isTrue();
    }
}
