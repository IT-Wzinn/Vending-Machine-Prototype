package com.vendingmachine.machine.repository;

import com.vendingmachine.machine.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MachineRepository extends JpaRepository<Machine, UUID> {

    Optional<Machine> findByMachineCode(String machineCode);
}
