package com.vendingmachine.admin.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminPasswordService {

    private final PasswordEncoder passwordEncoder;

    public AdminPasswordService() {
        // Standard BCrypt with cost factor 10 (cryptographic random salt per password)
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public AdminPasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String hashPassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return passwordEncoder.encode(password);
    }

    public boolean verifyPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(plainPassword, storedHash);
    }
}
