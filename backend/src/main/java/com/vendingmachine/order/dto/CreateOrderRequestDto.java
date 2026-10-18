package com.vendingmachine.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CreateOrderRequestDto {

    @NotBlank(message = "Mã máy không được để trống")
    private String machineId;

    @NotNull(message = "ID sản phẩm không được để trống")
    private UUID productId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Chỉ có thể đặt 1 sản phẩm mỗi lần.")
    @Max(value = 1, message = "Chỉ có thể đặt 1 sản phẩm mỗi lần.")
    private Integer quantity = 1;

    public CreateOrderRequestDto() {
    }

    public CreateOrderRequestDto(String machineId, UUID productId, Integer quantity) {
        this.machineId = machineId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
