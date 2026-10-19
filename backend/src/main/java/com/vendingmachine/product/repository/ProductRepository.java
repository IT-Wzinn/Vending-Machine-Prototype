package com.vendingmachine.product.repository;

import com.vendingmachine.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByMachineMachineCodeAndIsActiveTrueOrderBySlotNumberAsc(String machineCode);

    List<Product> findByMachineMachineCodeOrderBySlotNumberAsc(String machineCode);

    Optional<Product> findByIdAndMachineMachineCode(UUID id, String machineCode);

    Optional<Product> findByMachineMachineCodeAndSlotNumber(String machineCode, Integer slotNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.machine.machineCode = :machineCode")
    Optional<Product> findByIdAndMachineCodeForUpdate(@Param("id") UUID id, @Param("machineCode") String machineCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);
}
