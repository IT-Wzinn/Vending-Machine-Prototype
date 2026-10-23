package com.vendingmachine.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendingmachine.admin.dto.AdminLoginRequestDto;
import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.repository.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private com.vendingmachine.admin.repository.ReconciliationAuditLogRepository reconciliationAuditLogRepository;

    @Autowired
    private AdminPasswordService adminPasswordService;

    @Autowired
    private AdminJwtService adminJwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private AdminUser testAdmin;

    @BeforeEach
    void setUp() {
        reconciliationAuditLogRepository.deleteAll();
        adminUserRepository.deleteAll();

        testAdmin = new AdminUser();
        testAdmin.setId(UUID.randomUUID());
        testAdmin.setUsername("superadmin");
        testAdmin.setPasswordHash(adminPasswordService.hashPassword("SuperSecret@2026"));
        testAdmin.setTokenVersion(1);
        testAdmin.setCreatedAt(Instant.now());
        testAdmin.setUpdatedAt(Instant.now());
        testAdmin = adminUserRepository.save(testAdmin);
    }

    @Test
    @DisplayName("1. Đăng nhập admin thành công với username và password đúng")
    void testLogin_Success() throws Exception {
        AdminLoginRequestDto req = new AdminLoginRequestDto("superadmin", "SuperSecret@2026");

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("superadmin"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("2. Đăng nhập thất bại khi sai mật khẩu -> 401 UNAUTHORIZED")
    void testLogin_WrongPassword_Returns401() throws Exception {
        AdminLoginRequestDto req = new AdminLoginRequestDto("superadmin", "WrongPassword");

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("3. Đăng nhập thất bại khi user không tồn tại -> 401 UNAUTHORIZED")
    void testLogin_UnknownUser_Returns401() throws Exception {
        AdminLoginRequestDto req = new AdminLoginRequestDto("unknown_user", "AnyPassword");

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("4. Truy cập admin API không có token/cookie -> 401 UNAUTHORIZED")
    void testAccessAdminApi_NoToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reconciliation/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("5. Truy cập admin API với token hết hạn hoặc giả mạo -> 401 TOKEN_EXPIRED")
    void testAccessAdminApi_InvalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reconciliation/orders")
                        .header("Authorization", "Bearer invalid.fake.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("6. Truy cập admin API với tokenVersion cũ (đã thu hồi) -> 401 UNAUTHORIZED")
    void testAccessAdminApi_RevokedTokenVersion_Returns401() throws Exception {
        // Sinh token với tokenVersion = 1
        String token = adminJwtService.generateToken(testAdmin.getId(), testAdmin.getUsername(), 1);

        // Nâng tokenVersion trong DB lên 2 (mô phỏng logout / thu hồi quyền)
        testAdmin.setTokenVersion(2);
        adminUserRepository.save(testAdmin);

        mockMvc.perform(get("/api/v1/admin/reconciliation/orders")
                        .cookie(new Cookie("admin_access_token", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("7. Mutating request (POST) có token hợp lệ nhưng THIẾU header CSRF X-Requested-With -> 403 FORBIDDEN")
    void testMutatingRequest_MissingCsrfHeader_Returns403() throws Exception {
        String token = adminJwtService.generateToken(testAdmin.getId(), testAdmin.getUsername(), 1);
        UUID dummyOrderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/orders/" + dummyOrderId + "/reconciliation/decisions")
                        .cookie(new Cookie("admin_access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REFUND_PENDING\",\"reason\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_HEADER_MISSING"));
    }

    @Test
    @DisplayName("8. CORS preflight OPTIONS với header Authorization được chấp nhận 200 OK")
    void testCorsPreflight_WithAuthorizationHeader_Returns200() throws Exception {
        mockMvc.perform(options("/api/v1/admin/reconciliation/orders")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,X-Requested-With"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }

    @Test
    @DisplayName("9. AdminJwtService từ chối khởi tạo với secret rỗng hoặc quá ngắn (< 32 ký tự)")
    void testJwtSecretValidation() {
        MockEnvironment env = new MockEnvironment();
        assertThatThrownBy(() -> new AdminJwtService("", 28800, env, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin JWT secret is not configured");

        assertThatThrownBy(() -> new AdminJwtService("short-secret-key", 28800, env, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin JWT secret is too short");
    }

    @Test
    @DisplayName("10. AdminPasswordService sử dụng BCrypt ngẫu nhiên salt mỗi lần băm")
    void testBcryptPasswordHashing() {
        String hash1 = adminPasswordService.hashPassword("SecurePassword@2026");
        String hash2 = adminPasswordService.hashPassword("SecurePassword@2026");

        assertThat(hash1).startsWith("$2a$");
        assertThat(hash2).startsWith("$2a$");
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(adminPasswordService.verifyPassword("SecurePassword@2026", hash1)).isTrue();
        assertThat(adminPasswordService.verifyPassword("SecurePassword@2026", hash2)).isTrue();
        assertThat(adminPasswordService.verifyPassword("WrongPassword", hash1)).isFalse();
    }
}
