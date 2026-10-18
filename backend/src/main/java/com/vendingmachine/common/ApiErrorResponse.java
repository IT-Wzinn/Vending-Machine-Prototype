package com.vendingmachine.common;

import java.time.Instant;
import java.util.UUID;

public class ApiErrorResponse {

    private ErrorDetail error;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String code, String message, String requestId) {
        this.error = new ErrorDetail(code, message, Instant.now().toString(), requestId != null ? requestId : UUID.randomUUID().toString());
    }

    public static ApiErrorResponse of(String code, String message, String requestId) {
        return new ApiErrorResponse(code, message, requestId);
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public static class ErrorDetail {
        private String code;
        private String message;
        private String timestamp;
        private String requestId;

        public ErrorDetail() {
        }

        public ErrorDetail(String code, String message, String timestamp, String requestId) {
            this.code = code;
            this.message = message;
            this.timestamp = timestamp;
            this.requestId = requestId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }
}
