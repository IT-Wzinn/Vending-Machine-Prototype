package com.vendingmachine.admin.dto;

public class AdminLoginResponseDto {

    private String username;
    private String token;
    private long expiresInSeconds;
    private String message;

    public AdminLoginResponseDto() {
    }

    public AdminLoginResponseDto(String username, String token, long expiresInSeconds, String message) {
        this.username = username;
        this.token = token;
        this.expiresInSeconds = expiresInSeconds;
        this.message = message;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
