package com.vendingmachine.admin.controller;

import com.vendingmachine.admin.dto.ReconciliationDecisionRequestDto;
import com.vendingmachine.admin.dto.ReconciliationDecisionResponseDto;
import com.vendingmachine.admin.dto.ReconciliationOrderDetailDto;
import com.vendingmachine.admin.dto.ReconciliationOrderSummaryDto;
import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.security.AdminAuthInterceptor;
import com.vendingmachine.admin.service.AdminReconciliationService;
import com.vendingmachine.order.entity.OrderStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminReconciliationController {

    private final AdminReconciliationService adminReconciliationService;

    public AdminReconciliationController(AdminReconciliationService adminReconciliationService) {
        this.adminReconciliationService = adminReconciliationService;
    }

    @GetMapping("/reconciliation/orders")
    public ResponseEntity<Page<ReconciliationOrderSummaryDto>> getOrdersForReconciliation(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String orderCode) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReconciliationOrderSummaryDto> result = adminReconciliationService.getOrdersForReconciliation(status, orderCode, pageRequest);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/reconciliation/orders/{orderId}")
    public ResponseEntity<ReconciliationOrderDetailDto> getOrderDetailForReconciliation(@PathVariable UUID orderId) {
        ReconciliationOrderDetailDto dto = adminReconciliationService.getOrderDetailForReconciliation(orderId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/orders/{orderId}/reconciliation/decisions")
    public ResponseEntity<ReconciliationDecisionResponseDto> recordDecision(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReconciliationDecisionRequestDto request,
            HttpServletRequest httpRequest) {

        AdminUser actor = (AdminUser) httpRequest.getAttribute(AdminAuthInterceptor.CURRENT_ADMIN_ATTR);
        ReconciliationDecisionResponseDto response = adminReconciliationService.recordDecision(orderId, request, actor);
        return ResponseEntity.ok(response);
    }
}
