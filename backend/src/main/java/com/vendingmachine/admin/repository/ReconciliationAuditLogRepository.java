package com.vendingmachine.admin.repository;

import com.vendingmachine.admin.entity.ReconciliationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationAuditLogRepository extends JpaRepository<ReconciliationAuditLog, UUID> {

    List<ReconciliationAuditLog> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
