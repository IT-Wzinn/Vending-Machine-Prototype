package com.vendingmachine.admin.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminLoginRequestDto {

    @NotBlank(message = "Username không được để trống.")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống.")
    private String password;

    public AdminLoginRequestDto() {
    }

    public AdminLoginRequestDto(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
