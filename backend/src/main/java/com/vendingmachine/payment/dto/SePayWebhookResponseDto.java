package com.vendingmachine.payment.dto;

public class SePayWebhookResponseDto {

    private boolean success;
    private String message;

    public SePayWebhookResponseDto() {
    }

    public SePayWebhookResponseDto(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static SePayWebhookResponseDto ok(String message) {
        return new SePayWebhookResponseDto(true, message);
    }

    public static SePayWebhookResponseDto ok() {
        return new SePayWebhookResponseDto(true, "Webhook processed successfully");
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
