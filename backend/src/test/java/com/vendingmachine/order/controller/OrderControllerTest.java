package com.vendingmachine.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.GlobalExceptionHandler;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.order.dto.CreateOrderRequestDto;
import com.vendingmachine.order.dto.OrderResponseDto;
import com.vendingmachine.order.dto.OrderStatusResponseDto;
import com.vendingmachine.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private final UUID sampleOrderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID sampleProductId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private OrderResponseDto buildSampleOrderResponseDto() {
        OrderResponseDto.OrderProductDto productDto = new OrderResponseDto.OrderProductDto(
                sampleProductId,
                "Cà phê sữa đá",
                15000
        );

        OrderResponseDto.OrderPaymentDto paymentDto = new OrderResponseDto.OrderPaymentDto(
                "VM001AB3F",
                "00020101021238580010A0000007270128000697042201140001234567890208QRIBFTTA53037045405150005802VN62250821VM001AB3F6304E8A2",
                "DEMO123456",
                "DEMO SMART VENDING",
                "DEMO - Ngân hàng Thử nghiệm",
                15000,
                Instant.parse("2026-10-18T10:15:00Z"),
                null
        );

        OrderResponseDto.OrderDispensingDto dispensingDto = new OrderResponseDto.OrderDispensingDto(
                null,
                null,
                null,
                null,
                null
        );

        return new OrderResponseDto(
                sampleOrderId,
                "VM001-20261018-AB3F",
                "VM001",
                productDto,
                1,
                15000,
                "PENDING_PAYMENT",
                paymentDto,
                dispensingDto,
                Instant.parse("2026-10-18T10:00:00Z"),
                Instant.parse("2026-10-18T10:00:00Z")
        );
    }

    @Test
    @DisplayName("POST /api/v1/orders: 201 Created khi tạo đơn thành công")
    void createOrder_Success() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", sampleProductId, 1);
        OrderResponseDto response = buildSampleOrderResponseDto();

        when(orderService.createOrder(any(CreateOrderRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(sampleOrderId.toString()))
                .andExpect(jsonPath("$.data.publicOrderCode").value("VM001-20261018-AB3F"))
                .andExpect(jsonPath("$.data.machineId").value("VM001"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.product.productId").value(sampleProductId.toString()))
                .andExpect(jsonPath("$.data.product.name").value("Cà phê sữa đá"))
                .andExpect(jsonPath("$.data.product.priceVnd").value(15000))
                .andExpect(jsonPath("$.data.totalAmountVnd").value(15000))
                .andExpect(jsonPath("$.data.payment.transferReference").value("VM001AB3F"))
                .andExpect(jsonPath("$.data.payment.paymentQrData").exists())
                .andExpect(jsonPath("$.data.payment.paymentExpiresAt").value("2026-10-18T10:15:00Z"));
    }

    @Test
    @DisplayName("POST /api/v1/orders: 400 Bad Request với INVALID_QUANTITY khi quantity != 1")
    void createOrder_InvalidQuantity() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", sampleProductId, 2);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_QUANTITY))
                .andExpect(jsonPath("$.error.message").value("Chỉ có thể đặt 1 sản phẩm mỗi lần."));
    }

    @Test
    @DisplayName("POST /api/v1/orders: 400 Bad Request với VALIDATION_ERROR khi thiếu machineId")
    void createOrder_MissingMachineId() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("", sampleProductId, 1);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("POST /api/v1/orders: 404 Not Found khi máy không tồn tại (MACHINE_NOT_FOUND)")
    void createOrder_MachineNotFound() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM999", sampleProductId, 1);
        when(orderService.createOrder(any(CreateOrderRequestDto.class)))
                .thenThrow(new ResourceNotFoundException(ErrorCode.MACHINE_NOT_FOUND, "Máy bán hàng không tồn tại."));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.MACHINE_NOT_FOUND));
    }

    @Test
    @DisplayName("POST /api/v1/orders: 422 Unprocessable Entity khi máy không hoạt động (MACHINE_INACTIVE)")
    void createOrder_MachineInactive() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", sampleProductId, 1);
        when(orderService.createOrder(any(CreateOrderRequestDto.class)))
                .thenThrow(new BusinessException(ErrorCode.MACHINE_INACTIVE, "Máy bán hàng đang tạm dừng hoạt động.", HttpStatus.UNPROCESSABLE_ENTITY));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.MACHINE_INACTIVE));
    }

    @Test
    @DisplayName("POST /api/v1/orders: 404 Not Found khi sản phẩm không tồn tại (PRODUCT_NOT_FOUND)")
    void createOrder_ProductNotFound() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", sampleProductId, 1);
        when(orderService.createOrder(any(CreateOrderRequestDto.class)))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm không tồn tại hoặc đã ngừng kinhdong."));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("POST /api/v1/orders: 422 Unprocessable Entity khi sản phẩm không thuộc máy (PRODUCT_NOT_IN_MACHINE)")
    void createOrder_ProductNotInMachine() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", sampleProductId, 1);
        when(orderService.createOrder(any(CreateOrderRequestDto.class)))
                .thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_IN_MACHINE, "Sản phẩm không thuộc máy bán hàng này.", HttpStatus.UNPROCESSABLE_ENTITY));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PRODUCT_NOT_IN_MACHINE));
    }

    @Test
    @DisplayName("POST /api/v1/orders: 409 Conflict khi sản phẩm hết hàng khả dụng (PRODUCT_OUT_OF_STOCK)")
    void createOrder_OutOfStock() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("VM001", sampleProductId, 1);
        when(orderService.createOrder(any(CreateOrderRequestDto.class)))
                .thenThrow(new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK, "Sản phẩm đã hết hàng.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.PRODUCT_OUT_OF_STOCK));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}: 200 OK khi tìm thấy đơn hàng")
    void getOrder_Success() throws Exception {
        OrderResponseDto response = buildSampleOrderResponseDto();
        when(orderService.getOrder(sampleOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + sampleOrderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(sampleOrderId.toString()))
                .andExpect(jsonPath("$.data.publicOrderCode").value("VM001-20261018-AB3F"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}: 404 Not Found khi đơn hàng không tồn tại")
    void getOrder_NotFound() throws Exception {
        when(orderService.getOrder(sampleOrderId))
                .thenThrow(new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng với ID: " + sampleOrderId));

        mockMvc.perform(get("/api/v1/orders/" + sampleOrderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/status: 200 OK khi tra cứu trạng thái đơn hàng")
    void getOrderStatus_Success() throws Exception {
        OrderStatusResponseDto response = new OrderStatusResponseDto(
                sampleOrderId,
                "PENDING_PAYMENT",
                Instant.parse("2026-10-18T10:00:00Z"),
                "Chờ thanh toán qua mã QR."
        );
        when(orderService.getOrderStatus(sampleOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + sampleOrderId + "/status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(sampleOrderId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.message").value("Chờ thanh toán qua mã QR."));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/status: 404 Not Found khi đơn hàng không tồn tại")
    void getOrderStatus_NotFound() throws Exception {
        when(orderService.getOrderStatus(sampleOrderId))
                .thenThrow(new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng với ID: " + sampleOrderId));

        mockMvc.perform(get("/api/v1/orders/" + sampleOrderId + "/status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.ORDER_NOT_FOUND));
    }
}
