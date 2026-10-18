package com.vendingmachine.machine.controller;

import com.vendingmachine.common.exception.ErrorCode;
import com.vendingmachine.common.exception.GlobalExceptionHandler;
import com.vendingmachine.common.exception.ResourceNotFoundException;
import com.vendingmachine.machine.dto.MachineResponseDto;
import com.vendingmachine.machine.service.MachineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MachineController.class)
@Import(GlobalExceptionHandler.class)
class MachineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MachineService machineService;

    @Test
    @DisplayName("GET /api/v1/machines/{machineId}: Trả về 200 OK với đúng envelope JSON khi tìm thấy máy")
    void getMachine_Success() throws Exception {
        MachineResponseDto dto = new MachineResponseDto("VM001", "Máy bán hàng tầng 1", "ACTIVE", null);
        when(machineService.getMachineByCode("VM001")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/machines/VM001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.machineId").value("VM001"))
                .andExpect(jsonPath("$.data.name").value("Máy bán hàng tầng 1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.message").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/machines/{machineId}: Trả về 404 NOT_FOUND với chuẩn lỗi khi máy không tồn tại")
    void getMachine_NotFound() throws Exception {
        when(machineService.getMachineByCode("VM999"))
                .thenThrow(new ResourceNotFoundException(ErrorCode.MACHINE_NOT_FOUND, "Máy bán hàng không tồn tại."));

        mockMvc.perform(get("/api/v1/machines/VM999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MACHINE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Máy bán hàng không tồn tại."))
                .andExpect(jsonPath("$.error.timestamp").exists())
                .andExpect(jsonPath("$.error.requestId").exists());
    }
}
