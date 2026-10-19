package com.vendingmachine.dispense.controller;

import com.vendingmachine.dispense.dto.DispenseCommandResponseDto;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
import com.vendingmachine.dispense.service.DispenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DispenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DispenseService dispenseService;

    @Test
    @DisplayName("POST /api/v1/dispense/orders/{orderId}/trigger: Kích hoạt xuất hàng thành công")
    void triggerDispense_success() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID cmdId = UUID.randomUUID();
        when(dispenseService.triggerDispensing(orderId))
                .thenReturn(new DispenseTriggerResponseDto(orderId, cmdId, "DISPENSING", "Lệnh xuất hàng đã được phát."));

        mockMvc.perform(post("/api/v1/dispense/orders/{orderId}/trigger", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.commandId").value(cmdId.toString()))
                .andExpect(jsonPath("$.data.status").value("DISPENSING"));
    }

    @Test
    @DisplayName("GET /api/v1/dispense/orders/{orderId}/command: Lấy chi tiết lệnh xuất hàng")
    void getDispenseCommand_success() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID cmdId = UUID.randomUUID();
        DispenseCommandResponseDto dto = new DispenseCommandResponseDto();
        dto.setCommandId(cmdId);
        dto.setOrderId(orderId);
        dto.setMachineCode("VM001");
        dto.setSlotNumber(2);
        dto.setStatus("COMPLETED");
        dto.setAttemptCount(1);
        dto.setCreatedAt(Instant.now());

        when(dispenseService.getDispenseCommandByOrderId(orderId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/dispense/orders/{orderId}/command", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commandId").value(cmdId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.slotNumber").value(2));
    }
}
