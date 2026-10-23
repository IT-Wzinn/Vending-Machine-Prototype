package com.vendingmachine.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.admin.dto.ReconciliationDecisionRequestDto;
import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.entity.ReconciliationAuditLog;
import com.vendingmachine.admin.repository.AdminUserRepository;
import com.vendingmachine.admin.repository.ReconciliationAuditLogRepository;
import com.vendingmachine.admin.security.AdminJwtService;
import com.vendingmachine.admin.security.AdminPasswordService;
import com.vendingmachine.device.entity.DeviceEvent;
import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.entity.DispenseCommand;
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
import com.vendingmachine.payment.entity.PaymentTransaction;
import com.vendingmachine.payment.entity.VerificationStatus;
import com.vendingmachine.payment.repository.PaymentTransactionRepository;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminReconciliationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private ReconciliationAuditLogRepository reconciliationAuditLogRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminPasswordService adminPasswordService;

    @Autowired
    private AdminJwtService adminJwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private AdminUser testAdmin;
    private String adminToken;
    private Machine testMachine;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        reconciliationAuditLogRepository.deleteAll();
        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();
        adminUserRepository.deleteAll();

        // 1. Create and authenticate Admin
        testAdmin = new AdminUser();
        testAdmin.setId(UUID.randomUUID());
        testAdmin.setUsername("reconciliation_admin");
        testAdmin.setPasswordHash(adminPasswordService.hashPassword("ReconSecret@2026"));
        testAdmin.setTokenVersion(1);
        testAdmin.setCreatedAt(Instant.now());
        testAdmin.setUpdatedAt(Instant.now());
        testAdmin = adminUserRepository.save(testAdmin);

        adminToken = adminJwtService.generateToken(testAdmin.getId(), testAdmin.getUsername(), testAdmin.getTokenVersion());

        // 2. Create Machine & Product
        testMachine = new Machine(
                UUID.randomUUID(),
                "VM_RECON",
                "Máy kiểm thử Đối soát",
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
    }

    private Order createOrderInStatus(OrderStatus targetStatus) {
        CreateOrderRequestDto req = new CreateOrderRequestDto("VM_RECON", testProduct.getId(), 1);
        OrderResponseDto dto = orderService.createOrder(req);

        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow();
        order.setStatus(targetStatus);
        order.setPaidAt(Instant.now().minusSeconds(60));
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("1. Admin xem danh sách đơn hàng cần đối soát (phân trang và lọc theo trạng thái)")
    void test1_AdminGetOrdersList_ReturnsNeedsReconciliationOrders() throws Exception {
        Order o1 = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        Order o2 = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        Order o3 = createOrderInStatus(OrderStatus.PAID);

        mockMvc.perform(get("/api/v1/admin/reconciliation/orders")
                        .cookie(new Cookie("admin_access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("NEEDS_RECONCILIATION"));
    }

    @Test
    @DisplayName("2. Admin xem chi tiết đầy đủ đơn hàng đối soát mà không lộ secret")
    void test2_AdminGetOrderDetail_ReturnsFullInfoWithoutSecrets() throws Exception {
        Order order = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        UUID orderId = order.getId();

        // Thêm 1 payment transaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setProvider("SEPAY");
        tx.setProviderTransactionId("998877");
        tx.setOrder(order);
        tx.setAmountVnd(8000);
        tx.setTransferReference(order.getTransferReference());
        tx.setVerificationStatus(VerificationStatus.VERIFIED);
        tx.setTransactionStatus("SUCCESS");
        tx.setReceivedAt(Instant.now());
        tx.setCreatedAt(Instant.now());
        paymentTransactionRepository.save(tx);

        // Thêm 1 dispense command và 1 device event
        DispenseCommand cmd = new DispenseCommand();
        cmd.setCommandId(UUID.randomUUID());
        cmd.setOrder(order);
        cmd.setMachine(testMachine);
        cmd.setSlotNumber(1);
        cmd.setStatus("TIMEOUT");
        cmd.setFailureCode("TIMEOUT");
        cmd.setFailureMessage("Device timeout");
        cmd.setCreatedAt(Instant.now());
        dispenseCommandRepository.save(cmd);

        DeviceEvent event = new DeviceEvent();
        event.setEventId(UUID.randomUUID());
        event.setCommandId(cmd.getCommandId());
        event.setMachine(testMachine);
        event.setEventType("COMMAND_ACCEPTED");
        event.setSlotNumber(1);
        event.setCreatedAt(Instant.now());
        deviceEventRepository.save(event);

        mockMvc.perform(get("/api/v1/admin/reconciliation/orders/" + orderId)
                        .cookie(new Cookie("admin_access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("NEEDS_RECONCILIATION"))
                .andExpect(jsonPath("$.productName").value("Aqua 500ml"))
                .andExpect(jsonPath("$.paymentTransactions").isArray())
                .andExpect(jsonPath("$.paymentTransactions[0].providerTransactionId").value("998877"))
                .andExpect(jsonPath("$.dispenseCommands[0].status").value("TIMEOUT"))
                .andExpect(jsonPath("$.deviceEvents[0].eventType").value("COMMAND_ACCEPTED"));
    }

    @Test
    @DisplayName("3. Quyết định REFUND_PENDING: lưu audit log, đơn giữ nguyên NEEDS_RECONCILIATION, reservation KHÔNG đổi")
    void test3_RecordDecision_RefundPending_PreservesReservationAndLogsAudit() throws Exception {
        Order order = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        UUID orderId = order.getId();

        ReconciliationDecisionRequestDto req = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.REFUND_PENDING,
                "Chờ bộ phận kế toán đối soát giao dịch ngân hàng",
                null
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + orderId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.currentStatus").value("NEEDS_RECONCILIATION"))
                .andExpect(jsonPath("$.reservationReleased").value(false));

        // Kiểm tra database: reservation vẫn được giữ nguyên
        Product prod = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prod.getReservedQuantity()).isEqualTo(1);
        assertThat(prod.getStockQuantity()).isEqualTo(10);

        // Kiểm tra audit log đã được ghi nhận trong DB
        List<ReconciliationAuditLog> logs = reconciliationAuditLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("REFUND_PENDING");
        assertThat(logs.get(0).getAdminUser().getUsername()).isEqualTo("reconciliation_admin");
    }

    @Test
    @DisplayName("4. Quyết định REFUND_CONFIRMED khi THIẾU confirmationReference -> Bị từ chối 400 Bad Request")
    void test4_RecordDecision_RefundConfirmed_WithoutReference_Returns400() throws Exception {
        Order order = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        UUID orderId = order.getId();

        ReconciliationDecisionRequestDto req = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.REFUND_CONFIRMED,
                "Xác nhận hoàn tiền nhưng thiếu mã tham chiếu ngân hàng",
                "" // Blank confirmation reference!
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + orderId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Xác nhận trạng thái không bị thay đổi
        Order orderAfter = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        Product prod = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prod.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("5. Quyết định REFUND_CONFIRMED có mã tham chiếu ngân hàng: đơn CANCELLED, giải phóng reservation, stock không đổi")
    void test5_RecordDecision_RefundConfirmed_WithReference_CancelsOrderAndReleasesReservation() throws Exception {
        Order order = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        UUID orderId = order.getId();

        ReconciliationDecisionRequestDto req = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.REFUND_CONFIRMED,
                "Đã hoàn tiền thủ công qua Internet Banking Vietcombank cho khách",
                "VCB_REFUND_TX_987654321"
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + orderId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("REFUND_CONFIRMED"))
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.confirmationReference").value("VCB_REFUND_TX_987654321"))
                .andExpect(jsonPath("$.reservationReleased").value(true));

        // Kiểm tra trạng thái đơn hàng là CANCELLED
        Order orderAfter = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Kiểm tra reservation được giải phóng (1 -> 0), stock giữ nguyên (10)
        Product prod = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prod.getReservedQuantity()).isEqualTo(0);
        assertThat(prod.getStockQuantity()).isEqualTo(10);

        // Kiểm tra audit log
        List<ReconciliationAuditLog> logs = reconciliationAuditLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("REFUND_CONFIRMED");
        assertThat(logs.get(0).getNewStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("6. Ghi nhận quyết định trên đơn đã COMPLETED hoặc CANCELLED -> 409 Conflict")
    void test6_RecordDecision_OnCompletedOrCancelledOrder_Returns409Conflict() throws Exception {
        Order completedOrder = createOrderInStatus(OrderStatus.COMPLETED);

        ReconciliationDecisionRequestDto req = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.REFUND_PENDING,
                "Cố gắng đối soát đơn đã hoàn tất",
                null
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + completedOrder.getId() + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("7. Quyết định MANUAL_DISPENSE_APPROVED ghi nhận vào audit trail mà không kích hoạt motor mù quáng")
    void test7_RecordDecision_ManualDispenseApproved_RecordsInAuditTrailWithoutMotorRun() throws Exception {
        Order order = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        UUID orderId = order.getId();

        ReconciliationDecisionRequestDto req = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.MANUAL_DISPENSE_APPROVED,
                "Khách hàng khiếu nại tại chỗ, admin xác nhận nhả bù thủ công",
                "ON_SITE_VERIFIED_BY_ADMIN"
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + orderId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("MANUAL_DISPENSE_APPROVED"))
                .andExpect(jsonPath("$.currentStatus").value("NEEDS_RECONCILIATION"))
                .andExpect(jsonPath("$.reservationReleased").value(false));

        // Kiểm tra không có dispense command mới nào được tạo tự động
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderId(orderId);
        assertThat(commands).isEmpty();

        // Kiểm tra audit log
        List<ReconciliationAuditLog> logs = reconciliationAuditLogRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("MANUAL_DISPENSE_APPROVED");
    }

    @Test
    @DisplayName("8. Ghi nhận quyết định với Order ID không tồn tại -> 404 NOT FOUND")
    void test8_RecordDecision_OrderNotFound_Returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        ReconciliationDecisionRequestDto req = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.INVESTIGATION_NOTE,
                "Kiểm tra đơn không tồn tại",
                null
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + nonExistentId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("9. Thao tác đối soát lặp lại trên đơn đã hoàn tiền (CANCELLED) bị từ chối bảo đảm idempotency")
    void test9_DuplicateRefundConfirmed_RejectedIdempotently() throws Exception {
        Order order = createOrderInStatus(OrderStatus.NEEDS_RECONCILIATION);
        UUID orderId = order.getId();

        ReconciliationDecisionRequestDto req = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.REFUND_CONFIRMED,
                "Hoàn tiền lần 1",
                "REFUND_TX_001"
        );

        // Lần 1: Thành công
        mockMvc.perform(post("/api/v1/admin/orders/" + orderId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Lần 2: Bị từ chối vì đơn đã CANCELLED
        mockMvc.perform(post("/api/v1/admin/orders/" + orderId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("10. Đơn hết hạn đã nhả reservation, thanh toán muộn rồi refund: KHÔNG trừ reservation của đơn khác")
    void test10_RefundConfirmed_OnLatePaymentExpiredOrder_DoesNotStealOtherOrderReservation() throws Exception {
        testProduct.setStockQuantity(5);
        testProduct.setReservedQuantity(0);
        testProduct = productRepository.save(testProduct);

        // 1. Khách A tạo Order A -> reserved=1, is_reservation_held=true
        CreateOrderRequestDto reqA = new CreateOrderRequestDto("VM_RECON", testProduct.getId(), 1);
        OrderResponseDto dtoA = orderService.createOrder(reqA);
        Order orderA = orderRepository.findById(dtoA.getOrderId()).orElseThrow();
        assertThat(orderA.isReservationHeld()).isTrue();

        Product prodAfterA = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterA.getReservedQuantity()).isEqualTo(1);

        // 2. Order A hết hạn -> expireOrderIfPending nhả reservation -> reserved=0, is_reservation_held=false
        orderService.expireOrderIfPending(orderA.getId());
        orderA = orderRepository.findById(orderA.getId()).orElseThrow();
        assertThat(orderA.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(orderA.isReservationHeld()).isFalse();

        Product prodAfterExpire = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterExpire.getReservedQuantity()).isEqualTo(0);

        // 3. Khách A chuyển tiền muộn -> Order A chuyển trạng thái thành NEEDS_RECONCILIATION
        orderA.setStatus(OrderStatus.NEEDS_RECONCILIATION);
        orderA = orderRepository.save(orderA);

        // 4. Khách B tạo Order B -> reserved=1, is_reservation_held=true
        CreateOrderRequestDto reqB = new CreateOrderRequestDto("VM_RECON", testProduct.getId(), 1);
        OrderResponseDto dtoB = orderService.createOrder(reqB);
        Order orderB = orderRepository.findById(dtoB.getOrderId()).orElseThrow();
        assertThat(orderB.isReservationHeld()).isTrue();

        Product prodAfterB = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfterB.getReservedQuantity()).isEqualTo(1);

        // 5. Admin thực hiện REFUND_CONFIRMED cho Order A (vốn đã nhả reservation từ trước)
        ReconciliationDecisionRequestDto refundReq = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.REFUND_CONFIRMED,
                "Hoàn tiền do khách chuyển khoản sau khi đơn hết hạn",
                "REFUND_LATE_A_001"
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + orderA.getId() + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.reservationReleased").value(false));

        // 6. KIỂM CHỨNG QUAN TRỌNG:
        // - Order A đã CANCELLED, reservationHeld = false
        // - Product reservedQuantity VẪN PHẢI BẰNG 1 (giữ chỗ của Order B không bị trừ nhầm!)
        // - StockQuantity giữ nguyên = 5
        orderA = orderRepository.findById(orderA.getId()).orElseThrow();
        assertThat(orderA.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderA.isReservationHeld()).isFalse();

        Product prodFinal = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodFinal.getReservedQuantity()).isEqualTo(1);
        assertThat(prodFinal.getStockQuantity()).isEqualTo(5);

        // Order B vẫn nguyên vẹn
        orderB = orderRepository.findById(orderB.getId()).orElseThrow();
        assertThat(orderB.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderB.isReservationHeld()).isTrue();
    }

    @Test
    @DisplayName("11. Refund hợp lệ với đơn thực sự đang giữ reservation -> Giải phóng reservation an toàn")
    void test11_RefundConfirmed_ValidHeldReservation_ReleasesReservationSuccessfully() throws Exception {
        testProduct.setStockQuantity(5);
        testProduct.setReservedQuantity(0);
        testProduct = productRepository.save(testProduct);

        CreateOrderRequestDto req = new CreateOrderRequestDto("VM_RECON", testProduct.getId(), 1);
        OrderResponseDto dto = orderService.createOrder(req);
        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.NEEDS_RECONCILIATION);
        order = orderRepository.save(order);

        Product prodBefore = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodBefore.getReservedQuantity()).isEqualTo(1);

        ReconciliationDecisionRequestDto refundReq = new ReconciliationDecisionRequestDto(
                ReconciliationDecisionRequestDto.ReconciliationDecision.REFUND_CONFIRMED,
                "Hoàn tiền đơn lỗi nhả hàng",
                "REFUND_HELD_001"
        );

        mockMvc.perform(post("/api/v1/admin/orders/" + order.getId() + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", adminToken))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.reservationReleased").value(true));

        Product prodAfter = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(prodAfter.getReservedQuantity()).isEqualTo(0);
        assertThat(prodAfter.getStockQuantity()).isEqualTo(5);

        order = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(order.isReservationHeld()).isFalse();
    }
}
