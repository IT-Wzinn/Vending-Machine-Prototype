package com.vendingmachine.product.dto;

import com.vendingmachine.product.entity.Product;
import java.util.UUID;

public class ProductResponseDto {

    private UUID productId;
    private String name;
    private String description;
    private String imageUrl;
    private Integer priceVnd;
    private Integer slotNumber;
    private Integer stockQuantity;
    private Boolean available;

    public ProductResponseDto() {
    }

    public ProductResponseDto(UUID productId, String name, String description, String imageUrl,
                              Integer priceVnd, Integer slotNumber, Integer stockQuantity, Boolean available) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.priceVnd = priceVnd;
        this.slotNumber = slotNumber;
        this.stockQuantity = stockQuantity;
        this.available = available;
    }

    public static ProductResponseDto fromEntity(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPriceVnd(),
                product.getSlotNumber(),
                product.getStockQuantity(),
                product.isAvailable()
        );
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getPriceVnd() {
        return priceVnd;
    }

    public void setPriceVnd(Integer priceVnd) {
        this.priceVnd = priceVnd;
    }

    public Integer getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Integer slotNumber) {
        this.slotNumber = slotNumber;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }
}
