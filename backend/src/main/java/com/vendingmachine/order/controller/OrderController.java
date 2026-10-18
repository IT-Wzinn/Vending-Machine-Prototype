package com.vendingmachine.order.controller;

import com.vendingmachine.common.ApiResponse;
import com.vendingmachine.order.dto.CreateOrderRequestDto;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.dto.OrderStatusResponseDto;
import com.vendingmachine.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(@Valid @RequestBody CreateOrderRequestDto request) {
        OrderResponseDto dto = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(dto));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(@PathVariable UUID orderId) {
        OrderResponseDto dto = orderService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderStatusResponseDto>> getOrderStatus(@PathVariable UUID orderId) {
        OrderStatusResponseDto dto = orderService.getOrderStatus(orderId);
        return ResponseEntity.ok(ApiResponse.of(dto));
    }
}
