package com.vendingmachine.dispense.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.dispense.simulator.enabled=false")
class SimulationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Bảo mật: Khi app.dispense.simulator.enabled=false, tất cả endpoint /api/v1/simulation/** đều bị vô hiệu hóa (404)")
    void simulationEndpoints_disabledWhenPropertyIsFalse() throws Exception {
        mockMvc.perform(get("/api/v1/simulation/device/status"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/simulation/device/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"FAILURE\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/simulation/device/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"DISPENSE_RESULT\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/simulation/device/reset"))
                .andExpect(status().isNotFound());
    }
}
