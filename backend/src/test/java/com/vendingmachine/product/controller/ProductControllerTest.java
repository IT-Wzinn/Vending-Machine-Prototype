package com.vendingmachine.product.controller;

import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.GlobalExceptionHandler;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.product.dto.ProductCatalogResponseDto;
import com.vendingmachine.product.dto.ProductResponseDto;
import com.vendingmachine.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("GET /api/v1/machines/{machineId}/products: Trả về 200 OK và danh sách sản phẩm đúng cấu trúc hợp đồng")
    void getProducts_Success() throws Exception {
        ProductResponseDto p1 = new ProductResponseDto(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "Nước suối Aqua 500ml",
                "Nước uống tinh khiết",
                "https://example.com/aqua.jpg",
                8000,
                1,
                5,
                true
        );

        ProductResponseDto p3 = new ProductResponseDto(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                "Kẹo cao su Doublemint",
                null,
                null,
                5000,
                3,
                0,
                false
        );

        ProductCatalogResponseDto catalog = new ProductCatalogResponseDto("VM001", List.of(p1, p3));
        when(productService.getProductCatalog("VM001")).thenReturn(catalog);

        mockMvc.perform(get("/api/v1/machines/VM001/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.machineId").value("VM001"))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products[0].name").value("Nước suối Aqua 500ml"))
                .andExpect(jsonPath("$.data.products[0].priceVnd").value(8000))
                .andExpect(jsonPath("$.data.products[0].slotNumber").value(1))
                .andExpect(jsonPath("$.data.products[0].stockQuantity").value(5))
                .andExpect(jsonPath("$.data.products[0].available").value(true))
                .andExpect(jsonPath("$.data.products[1].name").value("Kẹo cao su Doublemint"))
                .andExpect(jsonPath("$.data.products[1].available").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/machines/{machineId}/products: Trả về 404 NOT_FOUND khi máy không tồn tại")
    void getProducts_MachineNotFound() throws Exception {
        when(productService.getProductCatalog("VM999"))
                .thenThrow(new ResourceNotFoundException(ErrorCode.MACHINE_NOT_FOUND, "Máy bán hàng không tồn tại."));

        mockMvc.perform(get("/api/v1/machines/VM999/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MACHINE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Máy bán hàng không tồn tại."))
                .andExpect(jsonPath("$.error.timestamp").exists())
                .andExpect(jsonPath("$.error.requestId").exists());
    }

    @Test
    @DisplayName("GET /api/v1/machines/{machineId}/products: Trả về 422 UNPROCESSABLE_ENTITY khi máy INACTIVE")
    void getProducts_MachineInactive() throws Exception {
        when(productService.getProductCatalog("VM002"))
                .thenThrow(new BusinessException(
                        ErrorCode.MACHINE_INACTIVE,
                        "Máy bán hàng hiện đang tạm ngừng hoạt động.",
                        HttpStatus.UNPROCESSABLE_ENTITY
                ));

        mockMvc.perform(get("/api/v1/machines/VM002/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MACHINE_INACTIVE"))
                .andExpect(jsonPath("$.error.message").value("Máy bán hàng hiện đang tạm ngừng hoạt động."))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }
}
