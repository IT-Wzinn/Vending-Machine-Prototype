package com.vendingmachine.admin.service;

import com.vendingmachine.admin.dto.AdminLoginRequestDto;
import com.vendingmachine.admin.dto.AdminLoginResponseDto;
import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.repository.AdminUserRepository;
import com.vendingmachine.admin.security.AdminJwtService;
import com.vendingmachine.admin.security.AdminPasswordService;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AdminUserRepository adminUserRepository;
    private final AdminPasswordService adminPasswordService;
    private final AdminJwtService adminJwtService;
    private final long expirationSeconds;

    public AdminAuthService(
            AdminUserRepository adminUserRepository,
            AdminPasswordService adminPasswordService,
            AdminJwtService adminJwtService,
            @Value("${app.admin.jwt.expiration-seconds:28800}") long expirationSeconds) {
        this.adminUserRepository = adminUserRepository;
        this.adminPasswordService = adminPasswordService;
        this.adminJwtService = adminJwtService;
        this.expirationSeconds = expirationSeconds;
    }

    @Transactional(readOnly = true)
    public AdminLoginResponseDto login(AdminLoginRequestDto request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dữ liệu đăng nhập không hợp lệ.", HttpStatus.BAD_REQUEST);
        }

        AdminUser user = adminUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed: Username {} not found", request.getUsername());
                    return new BusinessException(ErrorCode.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED);
                });

        boolean passwordMatch = adminPasswordService.verifyPassword(request.getPassword(), user.getPasswordHash());
        if (!passwordMatch) {
            log.warn("Login failed: Incorrect password for user {}", request.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED);
        }

        String token = adminJwtService.generateToken(user.getId(), user.getUsername(), user.getTokenVersion());
        log.info("Admin {} logged in successfully", user.getUsername());

        return new AdminLoginResponseDto(user.getUsername(), token, expirationSeconds, "Đăng nhập thành công.");
    }
}
