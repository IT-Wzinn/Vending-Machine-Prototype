package com.vendingmachine.dispense.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/simulation/device/status: Xem trạng thái simulator với nhãn SIMULATION rõ ràng")
    void getStatus() throws Exception {
        mockMvc.perform(get("/api/v1/simulation/device/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adapterType").value("SIMULATED"))
                .andExpect(jsonPath("$.data.disclaimer").value("SIMULATION ONLY - NOT PHYSICAL HARDWARE"));
    }

    @Test
    @DisplayName("POST /api/v1/simulation/device/mode: Thay đổi kịch bản sang FAILURE")
    void setMode() throws Exception {
        mockMvc.perform(post("/api/v1/simulation/device/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"FAILURE\",\"async\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentScenario").value("FAILURE"))
                .andExpect(jsonPath("$.data.asyncMode").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/simulation/device/reset: Xóa sạch bộ nhớ NVS mô phỏng")
    void resetState() throws Exception {
        mockMvc.perform(post("/api/v1/simulation/device/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").exists());
    }
}
