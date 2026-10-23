package com.vendingmachine.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminJwtService {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String FORBIDDEN_SAMPLE_SECRET = "vending-machine-prototype-super-secret-admin-key-2026";

    private final String secret;
    private final long expirationSeconds;
    private final ObjectMapper objectMapper;

    public AdminJwtService(
            @Value("${app.admin.jwt.secret:}") String secret,
            @Value("${app.admin.jwt.expiration-seconds:28800}") long expirationSeconds,
            Environment environment,
            ObjectMapper objectMapper) {

        validateSecret(secret, environment);
        this.secret = secret.trim();
        this.expirationSeconds = expirationSeconds;
        this.objectMapper = objectMapper;
    }

    private void validateSecret(String secret, Environment environment) {
        if (secret == null || secret.trim().isBlank()) {
            throw new IllegalStateException(
                    "CRITICAL CONFIGURATION ERROR: Admin JWT secret is not configured. "
                            + "Please set the APP_ADMIN_JWT_SECRET environment variable (minimum 32 characters / 256 bits)."
            );
        }

        String trimmed = secret.trim();
        if (trimmed.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "CRITICAL CONFIGURATION ERROR: Admin JWT secret is too short (" + trimmed.length() + " chars). "
                            + "Minimum required length is " + MIN_SECRET_LENGTH + " characters (256 bits)."
            );
        }

        boolean isTestProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.toLowerCase().contains("test"));
        if (!isTestProfile && trimmed.contains(FORBIDDEN_SAMPLE_SECRET)) {
            throw new IllegalStateException(
                    "CRITICAL SECURITY ERROR: Known insecure default JWT secret detected in non-test profile. "
                            + "Please configure a unique, cryptographically strong APP_ADMIN_JWT_SECRET."
            );
        }
    }

    public String generateToken(UUID adminId, String username, int tokenVersion) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + expirationSeconds;

            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            );

            Map<String, Object> payload = Map.of(
                    "sub", username,
                    "adminId", adminId != null ? adminId.toString() : "",
                    "tokenVersion", tokenVersion,
                    "iat", now,
                    "exp", exp
            );

            String headerJson = objectMapper.writeValueAsString(header);
            String payloadJson = objectMapper.writeValueAsString(payload);

            String headerEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            String content = headerEncoded + "." + payloadEncoded;
            String signature = sign(content, secret);

            return content + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate admin JWT", e);
        }
    }

    public AdminTokenClaims parseAndValidateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String content = parts[0] + "." + parts[1];
        String expectedSignature = sign(content, secret);

        if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, Map.class);

            String username = (String) claims.get("sub");
            String adminIdStr = (String) claims.get("adminId");
            Number tokenVersion = (Number) claims.get("tokenVersion");
            Number exp = (Number) claims.get("exp");

            long now = Instant.now().getEpochSecond();
            if (exp == null || now > exp.longValue()) {
                log.info("Token expired for user: {}", username);
                return null;
            }

            UUID adminId = (adminIdStr != null && !adminIdStr.isBlank()) ? UUID.fromString(adminIdStr) : null;
            int version = tokenVersion != null ? tokenVersion.intValue() : 1;

            return new AdminTokenClaims(adminId, username, version, exp.longValue());
        } catch (Exception e) {
            log.warn("Invalid JWT payload: {}", e.getMessage());
            return null;
        }
    }

    private String sign(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }

    public static class AdminTokenClaims {
        private final UUID adminId;
        private final String username;
        private final int tokenVersion;
        private final long expirationEpochSecond;

        public AdminTokenClaims(UUID adminId, String username, int tokenVersion, long expirationEpochSecond) {
            this.adminId = adminId;
            this.username = username;
            this.tokenVersion = tokenVersion;
            this.expirationEpochSecond = expirationEpochSecond;
        }

        public UUID getAdminId() { return adminId; }
        public String getUsername() { return username; }
        public int getTokenVersion() { return tokenVersion; }
        public long getExpirationEpochSecond() { return expirationEpochSecond; }
    }
}
