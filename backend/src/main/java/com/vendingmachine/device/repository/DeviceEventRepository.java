package com.vendingmachine.device.repository;

import com.vendingmachine.device.entity.DeviceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceEventRepository extends JpaRepository<DeviceEvent, UUID> {

    boolean existsByEventId(UUID eventId);

    Optional<DeviceEvent> findByEventId(UUID eventId);

    List<DeviceEvent> findByCommandIdOrderByCreatedAtAsc(UUID commandId);

    List<DeviceEvent> findByMachineMachineCode(String machineCode);
}
