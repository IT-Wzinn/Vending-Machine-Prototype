package com.vendingmachine.payment;

import com.vendingmachine.device.repository.DeviceEventRepository;
import com.vendingmachine.dispense.adapter.simulated.SimulatedDeviceAdapter;
import com.vendingmachine.dispense.adapter.simulated.SimulationScenario;
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
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.dispense.auto-trigger=true",
        "app.dispense.simulator.default-scenario=SUCCESS",
        "app.dispense.simulator.async=false"
})
class PaymentAutoTriggerIntegrationTest {

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

    @Autowired
    private DispenseCommandRepository dispenseCommandRepository;

    @Autowired
    private DeviceEventRepository deviceEventRepository;

    @Autowired
    private SimulatedDeviceAdapter simulatedDeviceAdapter;

    @Value("${app.payment.sepay.api-key}")
    private String configuredApiKey;

    private Machine testMachine;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        simulatedDeviceAdapter.resetState();
        simulatedDeviceAdapter.setScenario(SimulationScenario.SUCCESS);
        simulatedDeviceAdapter.setAsyncMode(false);

        deviceEventRepository.deleteAll();
        dispenseCommandRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        machineRepository.deleteAll();

        testMachine = new Machine(
                UUID.randomUUID(),
                "VM_AUTO",
                "Máy kiểm thử Auto-trigger Dispense",
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
                5,
                0,
                true
        );
        testProduct = productRepository.save(testProduct);
    }

    private SePayWebhookDto createWebhookPayload(Long id, int amount, String transferRef) {
        SePayWebhookDto dto = new SePayWebhookDto();
        dto.setId(id);
        dto.setGateway("MBBank");
        dto.setAccountNumber("0987654321");
        dto.setTransactionDate("2026-10-20 04:00:00");
        dto.setTransferAmount(amount);
        dto.setTransferType("in");
        dto.setContent("Thanh toan " + transferRef);
        dto.setCode(transferRef);
        dto.setReferenceCode("MB" + id);
        return dto;
    }

    @Test
    @DisplayName("Thanh toán thành công tự động kích hoạt nhả hàng E2E: đơn COMPLETED, lệnh COMPLETED, tồn kho giảm chính xác")
    void paymentWebhook_AutoTriggersDispensing_E2ESuccess() {
        // 1. Khách đặt mua sản phẩm: stock = 5, reserved = 1
        CreateOrderRequestDto createReq = new CreateOrderRequestDto("VM_AUTO", testProduct.getId(), 1);
        OrderResponseDto orderDto = orderService.createOrder(createReq);
        UUID orderId = orderDto.getOrderId();

        Product productAfterOrder = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(productAfterOrder.getStockQuantity()).isEqualTo(5);
        assertThat(productAfterOrder.getReservedQuantity()).isEqualTo(1);

        Order initialOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(initialOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // 2. Nhận SePay Webhook xác nhận thanh toán 8.000 VND
        long txId = 20261020001L;
        SePayWebhookDto webhookDto = createWebhookPayload(txId, 8000, initialOrder.getTransferReference());
        String authHeader = "Apikey " + configuredApiKey;

        SePayWebhookResponseDto webhookResponse = paymentService.processSePayWebhook(authHeader, webhookDto);
        assertThat(webhookResponse.isSuccess()).isTrue();

        // 3. Hậu kiểm thanh toán
        List<PaymentTransaction> txList = paymentTransactionRepository.findByOrderId(orderId);
        assertThat(txList).hasSize(1);
        PaymentTransaction tx = txList.get(0);
        assertThat(tx.getProviderTransactionId()).isEqualTo(String.valueOf(txId));
        assertThat(tx.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(tx.getTransactionStatus()).isEqualTo("SUCCESS");

        // 4. Hậu kiểm lệnh nhả hàng: được tạo tự động và COMPLETED
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertThat(commands).hasSize(1);
        DispenseCommand cmd = commands.get(0);
        assertThat(cmd.getStatus()).isEqualTo("COMPLETED");
        assertThat(cmd.getSlotNumber()).isEqualTo(1);

        // 5. Hậu kiểm sự kiện thiết bị mô phỏng
        var events = deviceEventRepository.findByCommandIdOrderByCreatedAtAsc(cmd.getCommandId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("COMMAND_ACCEPTED");
        assertThat(events.get(1).getEventType()).isEqualTo("DISPENSE_RESULT");

        // 6. Hậu kiểm đơn hàng: chuyển thành COMPLETED
        Order completedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completedOrder.getPaidAt()).isNotNull();
        assertThat(completedOrder.getDispensingStartedAt()).isNotNull();
        assertThat(completedOrder.getCompletedAt()).isNotNull();

        // 7. Hậu kiểm kho: stock giảm 5 -> 4, reserved giảm 1 -> 0
        Product finalProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(finalProduct.getStockQuantity()).isEqualTo(4);
        assertThat(finalProduct.getReservedQuantity()).isEqualTo(0);
        assertThat(finalProduct.getAvailableStock()).isEqualTo(4);
    }

    @Test
    @DisplayName("Thanh toán trùng lặp cho đơn đã xử lý: không nhả hàng lần hai, tồn kho không giảm thêm")
    void paymentWebhook_DuplicatePayment_DoesNotTriggerDispensingTwice() {
        // 1. Tạo và hoàn tất đơn đầu tiên
        CreateOrderRequestDto createReq = new CreateOrderRequestDto("VM_AUTO", testProduct.getId(), 1);
        OrderResponseDto orderDto = orderService.createOrder(createReq);
        UUID orderId = orderDto.getOrderId();
        Order order = orderRepository.findById(orderId).orElseThrow();

        long firstTxId = 20261020002L;
        SePayWebhookDto firstPayload = createWebhookPayload(firstTxId, 8000, order.getTransferReference());
        String authHeader = "Apikey " + configuredApiKey;

        paymentService.processSePayWebhook(authHeader, firstPayload);

        // Xác nhận đã COMPLETED và có 1 lệnh nhả hàng
        assertThat(dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId)).hasSize(1);
        Product productAfterFirst = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(productAfterFirst.getStockQuantity()).isEqualTo(4);

        // 2. Gửi lại webhook với cùng mã giao dịch (idempotent webhook)
        SePayWebhookResponseDto repeatRes = paymentService.processSePayWebhook(authHeader, firstPayload);
        assertThat(repeatRes.isSuccess()).isTrue();
        assertThat(dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId)).hasSize(1);

        // 3. Gửi webhook thứ hai với mã giao dịch KHÁC (khách chuyển tiền lần 2 cho cùng 1 đơn)
        long secondTxId = 20261020003L;
        SePayWebhookDto secondPayload = createWebhookPayload(secondTxId, 8000, order.getTransferReference());
        SePayWebhookResponseDto secondRes = paymentService.processSePayWebhook(authHeader, secondPayload);

        assertThat(secondRes.isSuccess()).isTrue();

        // Kiểm tra không có lệnh nhả hàng mới
        assertThat(dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId)).hasSize(1);

        // Tồn kho vẫn giữ nguyên ở mức 4, không bị trừ thêm
        Product productAfterSecond = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(productAfterSecond.getStockQuantity()).isEqualTo(4);
        assertThat(productAfterSecond.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Thiết bị ngoại tuyến khi nhả hàng: đơn chuyển sang NEEDS_RECONCILIATION, thanh toán vẫn VERIFIED, kho được bảo toàn")
    void paymentWebhook_OfflineDevice_SafelyTransitionsToNeedsReconciliation() {
        simulatedDeviceAdapter.setScenario(SimulationScenario.OFFLINE);

        CreateOrderRequestDto createReq = new CreateOrderRequestDto("VM_AUTO", testProduct.getId(), 1);
        OrderResponseDto orderDto = orderService.createOrder(createReq);
        UUID orderId = orderDto.getOrderId();
        Order order = orderRepository.findById(orderId).orElseThrow();

        long txId = 20261020004L;
        SePayWebhookDto payload = createWebhookPayload(txId, 8000, order.getTransferReference());
        String authHeader = "Apikey " + configuredApiKey;

        SePayWebhookResponseDto response = paymentService.processSePayWebhook(authHeader, payload);
        assertThat(response.isSuccess()).isTrue();

        // Giao dịch thanh toán vẫn VERIFIED / SUCCESS (tiền đã vào tài khoản an toàn)
        List<PaymentTransaction> txList = paymentTransactionRepository.findByOrderId(orderId);
        assertThat(txList).hasSize(1);
        assertThat(txList.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        // Lệnh nhả hàng FAILED do DEVICE_OFFLINE
        List<DispenseCommand> commands = dispenseCommandRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(commands.get(0).getFailureCode()).isEqualTo("DEVICE_OFFLINE");

        // Đơn hàng chuyển sang NEEDS_RECONCILIATION để đối soát an toàn
        Order reconciledOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reconciledOrder.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);

        // Kho được bảo toàn: stock = 5, reserved = 1 (chờ nhân viên đối soát)
        Product product = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(product.getReservedQuantity()).isEqualTo(1);
    }
}
