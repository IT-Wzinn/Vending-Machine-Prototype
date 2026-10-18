package com.vendingmachine.order.service;

import com.vendingmachine.order.dto.CreateOrderRequestDto;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.dto.OrderStatusResponseDto;

import java.util.UUID;

public interface OrderService {

    OrderResponseDto createOrder(CreateOrderRequestDto request);

    OrderResponseDto getOrder(UUID orderId);

    OrderStatusResponseDto getOrderStatus(UUID orderId);

    boolean expireOrderIfPending(UUID orderId);

    int cleanupExpiredOrders();
}
