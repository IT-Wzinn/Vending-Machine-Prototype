package com.vendingmachine.payment.service;

import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.entity.Order;
import com.vendingmachine.payment.dto.SePayWebhookDto;
import com.vendingmachine.payment.dto.SePayWebhookResponseDto;

public interface PaymentService {

    /**
     * Creates real or demo payment instructions (including VietQR data) for the given order.
     */
    OrderResponseDto.OrderPaymentDto createPaymentInstructions(Order order);

    /**
     * Processes SePay webhook notification: verifies credentials, enforces idempotency,
     * matches transaction to pending order, and marks order as PAID if valid.
     */
    SePayWebhookResponseDto processSePayWebhook(String authHeader, SePayWebhookDto payload);
}
