package com.vendingmachine.dispense.repository;

import com.vendingmachine.dispense.entity.DispenseCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispenseCommandRepository extends JpaRepository<DispenseCommand, UUID> {

    Optional<DispenseCommand> findByCommandId(UUID commandId);

    List<DispenseCommand> findByOrderId(UUID orderId);

    List<DispenseCommand> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    boolean existsByCommandId(UUID commandId);

    boolean existsByOrderId(UUID orderId);

    List<DispenseCommand> findByStatusInAndCreatedAtBefore(Collection<String> statuses, Instant cutoff);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM DispenseCommand c WHERE c.status IN :statuses AND " +
            "((c.publishedAt IS NOT NULL AND c.publishedAt < :cutoff) OR (c.publishedAt IS NULL AND c.createdAt < :cutoff))")
    List<DispenseCommand> findTimedOutCommands(@org.springframework.data.repository.query.Param("statuses") Collection<String> statuses,
                                               @org.springframework.data.repository.query.Param("cutoff") Instant cutoff);
}
