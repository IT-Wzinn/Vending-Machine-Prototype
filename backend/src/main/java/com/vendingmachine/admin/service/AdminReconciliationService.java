package com.vendingmachine.admin.service;

import com.vendingmachine.admin.dto.ReconciliationDecisionRequestDto;
import com.vendingmachine.admin.dto.ReconciliationDecisionResponseDto;
import com.vendingmachine.admin.dto.ReconciliationOrderDetailDto;
import com.vendingmachine.admin.dto.ReconciliationOrderSummaryDto;
import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminReconciliationService {

    Page<ReconciliationOrderSummaryDto> getOrdersForReconciliation(OrderStatus status, String orderCode, Pageable pageable);

    ReconciliationOrderDetailDto getOrderDetailForReconciliation(UUID orderId);

    ReconciliationDecisionResponseDto recordDecision(UUID orderId, ReconciliationDecisionRequestDto request, AdminUser actor);
}
