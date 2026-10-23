package com.vendingmachine.admin.security;

import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.repository.AdminUserRepository;
import com.vendingmachine.common.exception.BusinessException;
import com.vendingmachine.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);
    public static final String CURRENT_ADMIN_ATTR = "currentAdminUser";

    private final AdminJwtService adminJwtService;
    private final AdminUserRepository adminUserRepository;

    public AdminAuthInterceptor(AdminJwtService adminJwtService, AdminUserRepository adminUserRepository) {
        this.adminJwtService = adminJwtService;
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // 1. Skip preflight CORS requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 2. Skip public login endpoint
        if (path.endsWith("/admin/auth/login")) {
            return true;
        }

        // 3. Extract JWT token from Cookie or Authorization header
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            log.warn("Unauthorized access attempt to {}: Missing admin credentials", path);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu xác thực tài khoản quản trị.", HttpStatus.UNAUTHORIZED);
        }

        // 4. Validate token cryptographic signature and expiration
        AdminJwtService.AdminTokenClaims claims = adminJwtService.parseAndValidateToken(token);
        if (claims == null) {
            log.warn("Unauthorized access attempt to {}: Invalid or expired JWT token", path);
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token quản trị không hợp lệ hoặc đã hết hạn.", HttpStatus.UNAUTHORIZED);
        }

        // 5. Stateful check against database token_version for revocation
        Optional<AdminUser> adminUserOpt = adminUserRepository.findByUsername(claims.getUsername());
        if (adminUserOpt.isEmpty()) {
            log.warn("Admin user {} not found in database", claims.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Tài khoản quản trị không tồn tại.", HttpStatus.UNAUTHORIZED);
        }

        AdminUser adminUser = adminUserOpt.get();
        if (!adminUser.getTokenVersion().equals(claims.getTokenVersion())) {
            log.warn("Token version mismatch for admin {}: token version {}, db version {}",
                    claims.getUsername(), claims.getTokenVersion(), adminUser.getTokenVersion());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập đã bị vô hiệu hóa.", HttpStatus.UNAUTHORIZED);
        }

        // 6. CSRF Protection for state-changing HTTP methods (POST, PUT, PATCH, DELETE)
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            String requestedWith = request.getHeader("X-Requested-With");
            if (!"XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                log.warn("CSRF check failed for {} {}: Missing or invalid X-Requested-With header", method, path);
                throw new BusinessException(ErrorCode.CSRF_HEADER_MISSING, "Thiếu header bảo mật CSRF: X-Requested-With: XMLHttpRequest", HttpStatus.FORBIDDEN);
            }
        }

        // 7. Store authenticated AdminUser in request scope
        request.setAttribute(CURRENT_ADMIN_ATTR, adminUser);
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Try cookie first (primary browser contract)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("admin_access_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }

        // 2. Fallback to Authorization: Bearer <token> (for API/integration test clients)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        return null;
    }
}
