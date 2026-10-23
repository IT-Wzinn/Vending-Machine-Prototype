package com.vendingmachine.dispense.controller;

import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.repository.AdminUserRepository;
import com.vendingmachine.admin.security.AdminJwtService;
import com.vendingmachine.admin.security.AdminPasswordService;
import com.vendingmachine.dispense.dto.DispenseCommandResponseDto;
import com.vendingmachine.dispense.dto.DispenseTriggerResponseDto;
import com.vendingmachine.dispense.service.DispenseService;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminPasswordService adminPasswordService;

    @Autowired
    private AdminJwtService adminJwtService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminUserRepository.deleteAll();

        AdminUser admin = new AdminUser();
        admin.setId(UUID.randomUUID());
        admin.setUsername("dispense_admin");
        admin.setPasswordHash(adminPasswordService.hashPassword("DispenseAdmin@2026"));
        admin.setTokenVersion(1);
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        admin = adminUserRepository.save(admin);

        adminToken = adminJwtService.generateToken(admin.getId(), admin.getUsername(), admin.getTokenVersion());
    }

    @Test
    @DisplayName("POST /api/v1/dispense/orders/{orderId}/trigger: Từ chối 401 khi không có thông tin xác thực")
    void triggerDispense_unauthorized_whenMissingCredentials() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/dispense/orders/{orderId}/trigger", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/dispense/orders/{orderId}/trigger: Từ chối 403 khi thiếu CSRF header X-Requested-With")
    void triggerDispense_forbidden_whenMissingCsrfHeader() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/dispense/orders/{orderId}/trigger", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_HEADER_MISSING"));
    }

    @Test
    @DisplayName("POST /api/v1/dispense/orders/{orderId}/trigger: Kích hoạt xuất hàng thành công khi có quyền Admin")
    void triggerDispense_success_whenAuthorized() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID cmdId = UUID.randomUUID();
        when(dispenseService.triggerDispensing(orderId))
                .thenReturn(new DispenseTriggerResponseDto(orderId, cmdId, "DISPENSING", "Lệnh xuất hàng đã được phát."));

        mockMvc.perform(post("/api/v1/dispense/orders/{orderId}/trigger", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.commandId").value(cmdId.toString()))
                .andExpect(jsonPath("$.data.status").value("DISPENSING"));
    }

    @Test
    @DisplayName("GET /api/v1/dispense/orders/{orderId}/command: Từ chối 401 khi không có thông tin xác thực")
    void getDispenseCommand_unauthorized_whenMissingCredentials() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/dispense/orders/{orderId}/command", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/dispense/orders/{orderId}/command: Lấy chi tiết lệnh xuất hàng thành công khi có quyền Admin")
    void getDispenseCommand_success_whenAuthorized() throws Exception {
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
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commandId").value(cmdId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.slotNumber").value(2));
    }
}
