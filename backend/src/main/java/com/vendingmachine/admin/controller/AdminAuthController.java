package com.vendingmachine.admin.controller;

import com.vendingmachine.admin.dto.AdminLoginRequestDto;
import com.vendingmachine.admin.dto.AdminLoginResponseDto;
import com.vendingmachine.admin.service.AdminAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Value("${app.admin.cookie.secure:false}")
    private boolean cookieSecure;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponseDto> login(@Valid @RequestBody AdminLoginRequestDto request, HttpServletResponse response) {
        AdminLoginResponseDto dto = adminAuthService.login(request);

        ResponseCookie cookie = ResponseCookie.from("admin_access_token", dto.getToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1")
                .maxAge(dto.getExpiresInSeconds())
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(dto);
    }
}
