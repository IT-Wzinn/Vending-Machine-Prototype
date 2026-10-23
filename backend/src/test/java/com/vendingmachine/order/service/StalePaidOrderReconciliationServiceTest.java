package com.vendingmachine.order.service;

import com.vendingmachine.dispense.repository.DispenseCommandRepository;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.order.entity.OrderStatus;
import com.vendingmachine.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StalePaidOrderReconciliationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DispenseCommandRepository dispenseCommandRepository;

    @Mock
    private ObjectProvider<StalePaidOrderReconciliationService> selfProvider;

    @InjectMocks
    private StalePaidOrderReconciliationServiceImpl reconciliationService;

    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reconciliationService, "maxAgeMinutes", 15);
    }

    @Test
    @DisplayName("tagStalePaidOrdersForReconciliation triggers proxy tagging and returns count")
    void testTagStaleOrders_Success() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findStalePaidOrderIdsForReconciliation(any(Instant.class)))
                .thenReturn(List.of(orderId));

        StalePaidOrderReconciliationService proxyMock = mock(StalePaidOrderReconciliationService.class);
        when(selfProvider.getIfAvailable()).thenReturn(proxyMock);
        when(proxyMock.tagSingleOrderIfEligible(orderId)).thenReturn(true);

        int count = reconciliationService.tagStalePaidOrdersForReconciliation();

        assertThat(count).isEqualTo(1);
        verify(orderRepository).findStalePaidOrderIdsForReconciliation(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(Instant.now());
        verify(proxyMock).tagSingleOrderIfEligible(orderId);
    }

    @Test
    @DisplayName("tagStalePaidOrdersForReconciliation returns 0 when no stale orders found")
    void testTagStaleOrders_EmptyList() {
        when(orderRepository.findStalePaidOrderIdsForReconciliation(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        int count = reconciliationService.tagStalePaidOrdersForReconciliation();

        assertThat(count).isEqualTo(0);
        verify(selfProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("Error processing one order does not stop loop for remaining orders")
    void testTagStaleOrders_ErrorInOneOrder_ContinuesForOthers() {
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        when(orderRepository.findStalePaidOrderIdsForReconciliation(any(Instant.class)))
                .thenReturn(List.of(order1, order2));

        StalePaidOrderReconciliationService proxyMock = mock(StalePaidOrderReconciliationService.class);
        when(selfProvider.getIfAvailable()).thenReturn(proxyMock);
        when(proxyMock.tagSingleOrderIfEligible(order1)).thenThrow(new RuntimeException("DB glitch"));
        when(proxyMock.tagSingleOrderIfEligible(order2)).thenReturn(true);

        int count = reconciliationService.tagStalePaidOrdersForReconciliation();

        assertThat(count).isEqualTo(1);
        verify(proxyMock).tagSingleOrderIfEligible(order1);
        verify(proxyMock).tagSingleOrderIfEligible(order2);
    }

    @Test
    @DisplayName("Query exception is handled gracefully, returning 0")
    void testTagStaleOrders_RepositoryThrowsException_Graceful() {
        when(orderRepository.findStalePaidOrderIdsForReconciliation(any(Instant.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        int count = reconciliationService.tagStalePaidOrdersForReconciliation();

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("tagSingleOrderIfEligible returns false when order status is not PAID")
    void testTagSingleOrder_WhenNotPaid_ReturnsFalse() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.DISPENSING);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        boolean result = reconciliationService.tagSingleOrderIfEligible(orderId);

        assertThat(result).isFalse();
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("tagSingleOrderIfEligible returns false when paidAt is null")
    void testTagSingleOrder_WhenPaidAtNull_ReturnsFalse() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(null);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        boolean result = reconciliationService.tagSingleOrderIfEligible(orderId);

        assertThat(result).isFalse();
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("tagSingleOrderIfEligible returns false when paidAt is too recent (not before cutoff)")
    void testTagSingleOrder_WhenPaidAtTooRecent_ReturnsFalse() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now().minus(5, ChronoUnit.MINUTES)); // Only 5 min old, threshold is 15 min

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        boolean result = reconciliationService.tagSingleOrderIfEligible(orderId);

        assertThat(result).isFalse();
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("tagSingleOrderIfEligible returns false when dispense command already exists")
    void testTagSingleOrder_WhenDispenseCommandExists_ReturnsFalse() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now().minus(20, ChronoUnit.MINUTES));

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(dispenseCommandRepository.existsByOrderId(orderId)).thenReturn(true);

        boolean result = reconciliationService.tagSingleOrderIfEligible(orderId);

        assertThat(result).isFalse();
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("tagSingleOrderIfEligible transitions order to NEEDS_RECONCILIATION when fully eligible")
    void testTagSingleOrder_WhenEligible_TransitionsToNeedsReconciliation() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setPublicOrderCode("VM001-TEST");
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now().minus(25, ChronoUnit.MINUTES));

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(dispenseCommandRepository.existsByOrderId(orderId)).thenReturn(false);

        boolean result = reconciliationService.tagSingleOrderIfEligible(orderId);

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEEDS_RECONCILIATION);
        verify(orderRepository).save(order);
    }
}
