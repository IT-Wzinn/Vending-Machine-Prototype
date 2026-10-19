package com.vendingmachine.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.GlobalExceptionHandler;
import com.vendingmachine.payment.dto.SePayWebhookDto;
import com.vendingmachine.payment.dto.SePayWebhookResponseDto;
import com.vendingmachine.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentWebhookController.class)
@Import(GlobalExceptionHandler.class)
class PaymentWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @Test
    @DisplayName("POST /api/v1/webhooks/payments/sepay: 200 OK khi webhook hợp lệ")
    void handleSePayWebhook_Success() throws Exception {
        SePayWebhookDto dto = new SePayWebhookDto();
        dto.setId(1001L);
        dto.setGateway("MBBank");
        dto.setTransferAmount(15000);
        dto.setContent("VM001AB3F thanh toan");
        dto.setTransferType("in");

        when(paymentService.processSePayWebhook(eq("Apikey valid-key"), any(SePayWebhookDto.class)))
                .thenReturn(SePayWebhookResponseDto.ok());

        mockMvc.perform(post("/api/v1/webhooks/payments/sepay")
                        .header("Authorization", "Apikey valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/payments/sepay: 401 Unauthorized khi thiếu hoặc sai API Key")
    void handleSePayWebhook_Unauthorized() throws Exception {
        SePayWebhookDto dto = new SePayWebhookDto();
        dto.setId(1002L);

        when(paymentService.processSePayWebhook(eq("Apikey invalid-key"), any(SePayWebhookDto.class)))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid SePay webhook API key", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/webhooks/payments/sepay")
                        .header("Authorization", "Apikey invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED));
    }
}
