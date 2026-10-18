package com.vendingmachine.product.controller;

import com.vendingmachine.common.ApiResponse;
import com.vendingmachine.product.dto.ProductCatalogResponseDto;
import com.vendingmachine.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/machines/{machineId}/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProductCatalogResponseDto>> getProducts(@PathVariable String machineId) {
        ProductCatalogResponseDto catalog = productService.getProductCatalog(machineId);
        return ResponseEntity.ok(ApiResponse.of(catalog));
    }
}
