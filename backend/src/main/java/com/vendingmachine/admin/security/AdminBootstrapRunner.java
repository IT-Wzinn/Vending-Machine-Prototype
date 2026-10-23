package com.vendingmachine.admin.security;

import com.vendingmachine.admin.entity.AdminUser;
import com.vendingmachine.admin.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe bootstrap runner for creating the first administrator account.
 * Requires explicit environment configuration (APP_ADMIN_BOOTSTRAP_USERNAME and APP_ADMIN_BOOTSTRAP_PASSWORD).
 * Never automatically creates superadmin accounts with weak or hardcoded passwords.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminUserRepository adminUserRepository;
    private final AdminPasswordService adminPasswordService;

    @Value("${app.admin.bootstrap.username:}")
    private String bootstrapUsername;

    @Value("${app.admin.bootstrap.password:}")
    private String bootstrapPassword;

    public AdminBootstrapRunner(AdminUserRepository adminUserRepository, AdminPasswordService adminPasswordService) {
        this.adminUserRepository = adminUserRepository;
        this.adminPasswordService = adminPasswordService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapUsername != null && !bootstrapUsername.isBlank()
                && bootstrapPassword != null && !bootstrapPassword.isBlank()) {

            if (bootstrapPassword.length() < 8) {
                log.error("Configured bootstrap admin password is too short (minimum 8 characters). Bootstrap skipped.");
                return;
            }

            if (adminUserRepository.findByUsername(bootstrapUsername).isEmpty()) {
                AdminUser admin = new AdminUser();
                admin.setId(UUID.randomUUID());
                admin.setUsername(bootstrapUsername.trim());
                admin.setPasswordHash(adminPasswordService.hashPassword(bootstrapPassword));
                admin.setTokenVersion(1);
                admin.setCreatedAt(Instant.now());
                admin.setUpdatedAt(Instant.now());
                adminUserRepository.save(admin);

                log.info("Successfully bootstrapped initial admin user from environment configuration: {}", bootstrapUsername);
            } else {
                log.info("Bootstrap admin user '{}' already exists in database; preserving existing credentials.", bootstrapUsername);
            }
        } else {
            long adminCount = adminUserRepository.count();
            if (adminCount == 0) {
                log.warn("SECURITY ADVISORY: No administrator accounts exist in the database. "
                        + "To initialize an admin account securely, provide APP_ADMIN_BOOTSTRAP_USERNAME and APP_ADMIN_BOOTSTRAP_PASSWORD.");
            }
        }
    }
}
