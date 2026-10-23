package com.vendingmachine.dispense.service;

import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispenseRecoveryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DispenseService dispenseService;

    @InjectMocks
    private DispenseRecoveryServiceImpl recoveryService;

    @Captor
    private ArgumentCaptor<Instant> minAgeCaptor;

    @Captor
    private ArgumentCaptor<Instant> maxAgeCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recoveryService, "minAgeSeconds", 30);
        ReflectionTestUtils.setField(recoveryService, "maxAgeMinutes", 15);
    }

    @Test
    @DisplayName("When orphaned PAID order exists after minAge, triggerDispensing is invoked")
    void whenOrphanedPaidOrderExists_afterMinAge_thenTriggerDispensingInvoked() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findOrphanedPaidOrderIdsForRecovery(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(orderId));

        DispenseTriggerResponseDto response = new DispenseTriggerResponseDto(
                orderId, UUID.randomUUID(), "DISPENSING", "Triggered");
        when(dispenseService.triggerDispensing(orderId)).thenReturn(response);

        int recovered = recoveryService.recoverOrphanedPaidOrders();

        assertThat(recovered).isEqualTo(1);
        verify(orderRepository).findOrphanedPaidOrderIdsForRecovery(minAgeCaptor.capture(), maxAgeCaptor.capture());
        verify(dispenseService).triggerDispensing(orderId);

        Instant minCutoff = minAgeCaptor.getValue();
        Instant maxCutoff = maxAgeCaptor.getValue();
        assertThat(minCutoff).isAfter(maxCutoff);
    }

    @Test
    @DisplayName("When no orphaned orders match query, triggerDispensing is not called")
    void whenNoOrphanedOrders_thenTriggerDispensingNotCalled() {
        when(orderRepository.findOrphanedPaidOrderIdsForRecovery(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        int recovered = recoveryService.recoverOrphanedPaidOrders();

        assertThat(recovered).isEqualTo(0);
        verify(dispenseService, never()).triggerDispensing(any());
    }

    @Test
    @DisplayName("When triggerDispensing throws exception for one order, loop continues for other orders")
    void whenTriggerDispensingFailsForOne_thenRecoveryContinuesForOthers() {
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();

        when(orderRepository.findOrphanedPaidOrderIdsForRecovery(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(order1, order2));

        when(dispenseService.triggerDispensing(order1))
                .thenThrow(new BusinessException(ErrorCode.ORDER_NOT_PAID, "Order status invalid", HttpStatus.CONFLICT));

        DispenseTriggerResponseDto okResponse = new DispenseTriggerResponseDto(
                order2, UUID.randomUUID(), "DISPENSING", "Triggered");
        when(dispenseService.triggerDispensing(order2)).thenReturn(okResponse);

        int recovered = recoveryService.recoverOrphanedPaidOrders();

        assertThat(recovered).isEqualTo(1);
        verify(dispenseService).triggerDispensing(order1);
        verify(dispenseService).triggerDispensing(order2);
    }

    @Test
    @DisplayName("When repository query throws unexpected exception, recovery service handles gracefully")
    void whenRepositoryThrowsException_thenRecoveryReturnsZeroGracefully() {
        when(orderRepository.findOrphanedPaidOrderIdsForRecovery(any(Instant.class), any(Instant.class)))
                .thenThrow(new RuntimeException("Database connectivity glitch"));

        int recovered = recoveryService.recoverOrphanedPaidOrders();

        assertThat(recovered).isEqualTo(0);
        verify(dispenseService, never()).triggerDispensing(any());
    }
}
