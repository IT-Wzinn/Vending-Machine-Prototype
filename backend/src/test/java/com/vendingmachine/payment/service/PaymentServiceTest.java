package com.vendingmachine.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import com.vendingmachine.payment.dto.SePayWebhookDto;
import com.vendingmachine.payment.dto.SePayWebhookResponseDto;
import com.vendingmachine.payment.entity.PaymentTransaction;
import com.vendingmachine.payment.entity.VerificationStatus;
import com.vendingmachine.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private VietQrService vietQrService;

    @Mock
    private com.vendingmachine.dispense.service.DispenseService dispenseService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final String API_KEY = "sepay-secret-test-key";
    private Order sampleOrder;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "configuredApiKey", API_KEY);
        ReflectionTestUtils.setField(paymentService, "demoMode", false);
        ReflectionTestUtils.setField(paymentService, "bankBin", "970422");
        ReflectionTestUtils.setField(paymentService, "accountNumber", "0345678999");
        ReflectionTestUtils.setField(paymentService, "bankName", "MBBank");
        ReflectionTestUtils.setField(paymentService, "accountName", "SMART VENDING");
        ReflectionTestUtils.setField(paymentService, "dispenseService", dispenseService);
        ReflectionTestUtils.setField(paymentService, "autoTriggerDispense", true);

        orderId = UUID.randomUUID();
        sampleOrder = new Order();
        sampleOrder.setId(orderId);
        sampleOrder.setPublicOrderCode("VM001-20261019-AB3F");
        sampleOrder.setTransferReference("VM001AB3F");
        sampleOrder.setTotalAmountVnd(15000);
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        sampleOrder.setPaymentExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Xác thực Webhook: từ chối khi thiếu header hoặc sai API Key (401)")
    void authenticateWebhook_InvalidOrMissing() {
        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(101L);

        // Thiếu header
        assertThatThrownBy(() -> paymentService.processSePayWebhook(null, payload))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        // Sai token
        assertThatThrownBy(() -> paymentService.processSePayWebhook("Apikey wrong-key", payload))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Xử lý Webhook thành công: chuyển PENDING_PAYMENT sang PAID, lưu PaymentTransaction VERIFIED")
    void processWebhook_Success_MarksPaid() {
        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5001L);
        payload.setTransferType("in");
        payload.setTransferAmount(15000);
        payload.setCode("VM001AB3F");
        payload.setContent("VM001AB3F chuyen khoan");
        payload.setTransactionDate("2026-10-19 10:00:00");

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5001")).thenReturn(false);
        when(orderRepository.findByTransferReference("VM001AB3F")).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.markOrderAsPaidIfPending(eq(orderId), any(Instant.class), any(Instant.class))).thenReturn(1);

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Apikey " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();

        // Kiểm tra lưu PaymentTransaction
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).saveAndFlush(captor.capture());
        PaymentTransaction savedTx = captor.getValue();

        assertThat(savedTx.getProviderTransactionId()).isEqualTo("5001");
        assertThat(savedTx.getAmountVnd()).isEqualTo(15000);
        assertThat(savedTx.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(savedTx.getTransactionStatus()).isEqualTo("SUCCESS");
        assertThat(savedTx.getOrder()).isEqualTo(sampleOrder);

        // Xác nhận markOrderAsPaidIfPending được gọi
        verify(orderRepository).markOrderAsPaidIfPending(eq(orderId), any(Instant.class), any(Instant.class));
        // Xác nhận auto-trigger nhả hàng được gọi
        verify(dispenseService).triggerDispensing(orderId);
    }

    @Test
    @DisplayName("Idempotency: Giao dịch đã tồn tại trong DB, trả về success ngay lập tức không xử lý lại")
    void processWebhook_DuplicateTransaction_Idempotent() {
        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5001L);

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5001")).thenReturn(true);

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Bearer " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();
        verifyNoInteractions(orderRepository);
        verify(paymentTransactionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(dispenseService);
    }

    @Test
    @DisplayName("Giao dịch không khớp mã tham chiếu: lưu UNMATCHED, không thay đổi đơn hàng")
    void processWebhook_UnmatchedOrder() {
        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5002L);
        payload.setTransferType("in");
        payload.setTransferAmount(15000);
        payload.setContent("Chuyen tien ung ho");

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5002")).thenReturn(false);

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Apikey " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getVerificationStatus()).isEqualTo(VerificationStatus.UNMATCHED);
        assertThat(captor.getValue().getOrder()).isNull();
        verifyNoInteractions(dispenseService);
    }

    @Test
    @DisplayName("Chuyển thiếu tiền (Underpaid): lưu UNDERPAID, không chuyển trạng thái đơn sang PAID")
    void processWebhook_Underpaid() {
        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5003L);
        payload.setTransferType("in");
        payload.setTransferAmount(10000); // 10.000 < 15.000
        payload.setCode("VM001AB3F");

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5003")).thenReturn(false);
        when(orderRepository.findByTransferReference("VM001AB3F")).thenReturn(Optional.of(sampleOrder));

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Apikey " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getVerificationStatus()).isEqualTo(VerificationStatus.UNDERPAID);
        assertThat(captor.getValue().getAmountVnd()).isEqualTo(10000);

        verify(orderRepository, never()).markOrderAsPaidIfPending(any(), any(), any());
        verifyNoInteractions(dispenseService);
    }

    @Test
    @DisplayName("Thanh toán đến sau khi đơn EXPIRED: lưu LATE_PAYMENT, chuyển đơn sang NEEDS_RECONCILIATION")
    void processWebhook_LatePayment_OrderExpired() {
        sampleOrder.setStatus(OrderStatus.EXPIRED);

        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5004L);
        payload.setTransferType("in");
        payload.setTransferAmount(15000);
        payload.setCode("VM001AB3F");

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5004")).thenReturn(false);
        when(orderRepository.findByTransferReference("VM001AB3F")).thenReturn(Optional.of(sampleOrder));

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Apikey " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getVerificationStatus()).isEqualTo(VerificationStatus.LATE_PAYMENT);

        // Chuyển sang NEEDS_RECONCILIATION để đối soát hoàn tiền
        verify(orderRepository).updateOrderStatusIfExpected(eq(orderId), eq(OrderStatus.EXPIRED), eq(OrderStatus.NEEDS_RECONCILIATION), any());
        verify(orderRepository, never()).markOrderAsPaidIfPending(any(), any(), any());
        verifyNoInteractions(dispenseService);
    }

    @Test
    @DisplayName("Khách chuyển tiền lần hai cho đơn đã PAID: lưu DUPLICATE, không xuất hàng lần hai")
    void processWebhook_MultiplePayments_AlreadyPaid() {
        sampleOrder.setStatus(OrderStatus.PAID);

        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5005L);
        payload.setTransferType("in");
        payload.setTransferAmount(15000);
        payload.setCode("VM001AB3F");

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5005")).thenReturn(false);
        when(orderRepository.findByTransferReference("VM001AB3F")).thenReturn(Optional.of(sampleOrder));

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Apikey " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getVerificationStatus()).isEqualTo(VerificationStatus.DUPLICATE);

        verify(orderRepository, never()).markOrderAsPaidIfPending(any(), any(), any());
        verifyNoInteractions(dispenseService);
    }

    @Test
    @DisplayName("Auto-trigger nhả hàng gặp ngoại lệ: không làm đổ vỡ webhook, thanh toán vẫn được ghi nhận PAID")
    void processWebhook_AutoTriggerException_HandledGracefully() {
        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5006L);
        payload.setTransferType("in");
        payload.setTransferAmount(15000);
        payload.setCode("VM001AB3F");
        payload.setContent("VM001AB3F chuyen khoan");
        payload.setTransactionDate("2026-10-19 10:00:00");

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5006")).thenReturn(false);
        when(orderRepository.findByTransferReference("VM001AB3F")).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.markOrderAsPaidIfPending(eq(orderId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(dispenseService.triggerDispensing(orderId)).thenThrow(new RuntimeException("Simulated adapter error"));

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Apikey " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();
        verify(paymentTransactionRepository).saveAndFlush(any(PaymentTransaction.class));
        verify(dispenseService).triggerDispensing(orderId);
    }

    @Test
    @DisplayName("Auto-trigger bị tắt (autoTriggerDispense = false): không gọi dispenseService")
    void processWebhook_AutoTriggerDisabled_DoesNotTriggerDispensing() {
        ReflectionTestUtils.setField(paymentService, "autoTriggerDispense", false);

        SePayWebhookDto payload = new SePayWebhookDto();
        payload.setId(5007L);
        payload.setTransferType("in");
        payload.setTransferAmount(15000);
        payload.setCode("VM001AB3F");
        payload.setContent("VM001AB3F chuyen khoan");
        payload.setTransactionDate("2026-10-19 10:00:00");

        when(paymentTransactionRepository.existsByProviderAndProviderTransactionId("SEPAY", "5007")).thenReturn(false);
        when(orderRepository.findByTransferReference("VM001AB3F")).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.markOrderAsPaidIfPending(eq(orderId), any(Instant.class), any(Instant.class))).thenReturn(1);

        SePayWebhookResponseDto response = paymentService.processSePayWebhook("Apikey " + API_KEY, payload);

        assertThat(response.isSuccess()).isTrue();
        verifyNoInteractions(dispenseService);
    }
}
