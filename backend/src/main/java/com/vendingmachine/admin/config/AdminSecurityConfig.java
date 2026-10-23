package com.vendingmachine.admin.config;

import com.vendingmachine.admin.repository.AdminUserRepository;
import com.vendingmachine.admin.security.AdminAuthInterceptor;
import com.vendingmachine.admin.security.AdminJwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminSecurityConfig {

    @Bean
    public AdminAuthInterceptor adminAuthInterceptor(AdminJwtService adminJwtService, AdminUserRepository adminUserRepository) {
        return new AdminAuthInterceptor(adminJwtService, adminUserRepository);
    }
}
