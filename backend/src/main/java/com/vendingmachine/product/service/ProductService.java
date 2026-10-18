package com.vendingmachine.product.service;

import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.service.MachineService;
import com.vendingmachine.product.dto.ProductCatalogResponseDto;
import com.vendingmachine.product.dto.ProductResponseDto;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final MachineService machineService;

    public ProductService(ProductRepository productRepository, MachineService machineService) {
        this.productRepository = productRepository;
        this.machineService = machineService;
    }

    public ProductCatalogResponseDto getProductCatalog(String machineCode) {
        // Validates that machine exists and is in ACTIVE state
        Machine machine = machineService.getActiveMachineEntity(machineCode);

        List<Product> products = productRepository.findByMachineMachineCodeAndIsActiveTrueOrderBySlotNumberAsc(machine.getMachineCode());

        List<ProductResponseDto> productDtos = products.stream()
                .map(ProductResponseDto::fromEntity)
                .toList();

        return new ProductCatalogResponseDto(machine.getMachineCode(), productDtos);
    }
}
