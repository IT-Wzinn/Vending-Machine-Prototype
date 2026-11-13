package com.vendingmachine.product.service;

import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.machine.entity.Machine;
import com.vendingmachine.machine.entity.MachineStatus;
import com.vendingmachine.machine.service.MachineService;
import com.vendingmachine.product.dto.ProductCatalogResponseDto;
import com.vendingmachine.product.dto.ProductResponseDto;
import com.vendingmachine.product.entity.Product;
import com.vendingmachine.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MachineService machineService;

    @InjectMocks
    private ProductService productService;

    private Machine activeMachine;
    private Product p1;
    private Product p2;
    private Product p3;

    @BeforeEach
    void setUp() {
        activeMachine = new Machine(
                UUID.randomUUID(),
                "VM001",
                "Máy bán hàng tầng 1",
                MachineStatus.ACTIVE,
                null
        );

        p1 = new Product(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                activeMachine,
                "Nước suối Aqua 500ml",
                "Nước uống tinh khiết Aqua",
                "https://example.com/aqua.jpg",
                8000,
                1,
                5,
                0,
                true
        );

        p2 = new Product(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                activeMachine,
                "Snack Lay's vị tự nhiên",
                null,
                null,
                15000,
                2,
                3,
                0,
                true
        );

        p3 = new Product(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                activeMachine,
                "Kẹo cao su Doublemint",
                null,
                null,
                5000,
                3,
                0, // out of stock
                0,
                true
        );
    }

    @Test
    @DisplayName("getProductCatalog: Thành công trả về danh sách 3 sản phẩm có availability chính xác")
    void getProductCatalog_Success() {
        when(machineService.getActiveMachineEntity("VM001")).thenReturn(activeMachine);
        when(productRepository.findByMachineMachineCodeAndIsActiveTrueOrderBySlotNumberAsc("VM001"))
                .thenReturn(List.of(p1, p2, p3));

        ProductCatalogResponseDto result = productService.getProductCatalog("VM001");

        assertThat(result).isNotNull();
        assertThat(result.getMachineId()).isEqualTo("VM001");
        assertThat(result.getProducts()).hasSize(3);

        // Slot 1
        assertThat(result.getProducts().get(0).getName()).isEqualTo("Nước suối Aqua 500ml");
        assertThat(result.getProducts().get(0).getPriceVnd()).isEqualTo(8000);
        assertThat(result.getProducts().get(0).getSlotNumber()).isEqualTo(1);
        assertThat(result.getProducts().get(0).getStockQuantity()).isEqualTo(5);
        assertThat(result.getProducts().get(0).getReservedQuantity()).isEqualTo(0);
        assertThat(result.getProducts().get(0).getAvailableStock()).isEqualTo(5);
        assertThat(result.getProducts().get(0).getAvailable()).isTrue();

        // Slot 2
        assertThat(result.getProducts().get(1).getName()).isEqualTo("Snack Lay's vị tự nhiên");
        assertThat(result.getProducts().get(1).getPriceVnd()).isEqualTo(15000);
        assertThat(result.getProducts().get(1).getSlotNumber()).isEqualTo(2);
        assertThat(result.getProducts().get(1).getStockQuantity()).isEqualTo(3);
        assertThat(result.getProducts().get(1).getReservedQuantity()).isEqualTo(0);
        assertThat(result.getProducts().get(1).getAvailableStock()).isEqualTo(3);
        assertThat(result.getProducts().get(1).getAvailable()).isTrue();

        // Slot 3 (Out of stock)
        assertThat(result.getProducts().get(2).getName()).isEqualTo("Kẹo cao su Doublemint");
        assertThat(result.getProducts().get(2).getPriceVnd()).isEqualTo(5000);
        assertThat(result.getProducts().get(2).getSlotNumber()).isEqualTo(3);
        assertThat(result.getProducts().get(2).getStockQuantity()).isEqualTo(0);
        assertThat(result.getProducts().get(2).getReservedQuantity()).isEqualTo(0);
        assertThat(result.getProducts().get(2).getAvailableStock()).isEqualTo(0);
        assertThat(result.getProducts().get(2).getAvailable()).isFalse();
    }

    @Test
    @DisplayName("getProductCatalog: Ma trận kiểm thử 5 kịch bản tồn kho (stockQuantity, reservedQuantity, availableStock, available)")
    void getProductCatalog_InventoryMatrixScenarios() {
        // Kịch bản 1: 2, 0, 2 -> Buyable
        Product sc1 = new Product(UUID.randomUUID(), activeMachine, "Aqua 1", "", null, 8000, 1, 2, 0, true);
        // Kịch bản 2: 2, 1, 1 -> Buyable
        Product sc2 = new Product(UUID.randomUUID(), activeMachine, "Aqua 2", "", null, 8000, 2, 2, 1, true);
        // Kịch bản 3: 2, 2, 0 -> Not buyable (Tình trạng lỗi thực tế trước đây)
        Product sc3 = new Product(UUID.randomUUID(), activeMachine, "Aqua 3", "", null, 8000, 3, 2, 2, true);

        when(machineService.getActiveMachineEntity("VM001")).thenReturn(activeMachine);
        when(productRepository.findByMachineMachineCodeAndIsActiveTrueOrderBySlotNumberAsc("VM001"))
                .thenReturn(List.of(sc1, sc2, sc3));

        ProductCatalogResponseDto result = productService.getProductCatalog("VM001");

        // Sc 1: 2, 0, 2 -> Buyable
        assertThat(sc1.getAvailableStock()).isEqualTo(2);
        assertThat(sc1.isAvailable()).isTrue();
        assertThat(result.getProducts().get(0).getStockQuantity()).isEqualTo(2);
        assertThat(result.getProducts().get(0).getReservedQuantity()).isEqualTo(0);
        assertThat(result.getProducts().get(0).getAvailableStock()).isEqualTo(2);
        assertThat(result.getProducts().get(0).getAvailable()).isTrue();

        // Sc 2: 2, 1, 1 -> Buyable
        assertThat(sc2.getAvailableStock()).isEqualTo(1);
        assertThat(sc2.isAvailable()).isTrue();
        assertThat(result.getProducts().get(1).getStockQuantity()).isEqualTo(2);
        assertThat(result.getProducts().get(1).getReservedQuantity()).isEqualTo(1);
        assertThat(result.getProducts().get(1).getAvailableStock()).isEqualTo(1);
        assertThat(result.getProducts().get(1).getAvailable()).isTrue();

        // Sc 3: 2, 2, 0 -> Not buyable
        assertThat(sc3.getAvailableStock()).isEqualTo(0);
        assertThat(sc3.isAvailable()).isFalse();
        assertThat(result.getProducts().get(2).getStockQuantity()).isEqualTo(2);
        assertThat(result.getProducts().get(2).getReservedQuantity()).isEqualTo(2);
        assertThat(result.getProducts().get(2).getAvailableStock()).isEqualTo(0);
        assertThat(result.getProducts().get(2).getAvailable()).isFalse();

        // Kịch bản 4: 0, 0, 0 -> Not buyable
        Product sc4 = new Product(UUID.randomUUID(), activeMachine, "Doublemint", "", null, 5000, 1, 0, 0, true);
        assertThat(sc4.getAvailableStock()).isEqualTo(0);
        assertThat(sc4.isAvailable()).isFalse();
        ProductResponseDto dto4 = ProductResponseDto.fromEntity(sc4);
        assertThat(dto4.getStockQuantity()).isEqualTo(0);
        assertThat(dto4.getReservedQuantity()).isEqualTo(0);
        assertThat(dto4.getAvailableStock()).isEqualTo(0);
        assertThat(dto4.getAvailable()).isFalse();

        // Kịch bản 5: 5, 3, 2 -> Buyable
        Product sc5 = new Product(UUID.randomUUID(), activeMachine, "Snack Lays", "", null, 15000, 2, 5, 3, true);
        assertThat(sc5.getAvailableStock()).isEqualTo(2);
        assertThat(sc5.isAvailable()).isTrue();
        ProductResponseDto dto5 = ProductResponseDto.fromEntity(sc5);
        assertThat(dto5.getStockQuantity()).isEqualTo(5);
        assertThat(dto5.getReservedQuantity()).isEqualTo(3);
        assertThat(dto5.getAvailableStock()).isEqualTo(2);
        assertThat(dto5.getAvailable()).isTrue();
    }

    @Test
    @DisplayName("getProductCatalog: Ném ResourceNotFoundException khi máy không tồn tại")
    void getProductCatalog_MachineNotFound() {
        when(machineService.getActiveMachineEntity("NON_EXISTENT"))
                .thenThrow(new ResourceNotFoundException(ErrorCode.MACHINE_NOT_FOUND, "Máy bán hàng không tồn tại."));

        assertThatThrownBy(() -> productService.getProductCatalog("NON_EXISTENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Máy bán hàng không tồn tại.");
    }

    @Test
    @DisplayName("getProductCatalog: Ném BusinessException khi máy tạm ngừng hoạt động (INACTIVE)")
    void getProductCatalog_MachineInactive() {
        when(machineService.getActiveMachineEntity("VM002"))
                .thenThrow(new BusinessException(ErrorCode.MACHINE_INACTIVE, "Máy bán hàng hiện đang tạm ngừng hoạt động.", HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> productService.getProductCatalog("VM002"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Máy bán hàng hiện đang tạm ngừng hoạt động.")
                .satisfies(ex -> assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
