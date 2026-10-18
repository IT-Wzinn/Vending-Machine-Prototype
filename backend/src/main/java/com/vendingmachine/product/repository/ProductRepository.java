package com.vendingmachine.product.repository;

import com.vendingmachine.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
