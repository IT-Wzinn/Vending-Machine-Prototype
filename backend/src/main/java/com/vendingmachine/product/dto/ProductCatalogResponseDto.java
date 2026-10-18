package com.vendingmachine.product.dto;

import java.util.List;

public class ProductCatalogResponseDto {

    private String machineId;
    private List<ProductResponseDto> products;

    public ProductCatalogResponseDto() {
    }

    public ProductCatalogResponseDto(String machineId, List<ProductResponseDto> products) {
        this.machineId = machineId;
        this.products = products;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public List<ProductResponseDto> getProducts() {
        return products;
    }

    public void setProducts(List<ProductResponseDto> products) {
        this.products = products;
    }
}
