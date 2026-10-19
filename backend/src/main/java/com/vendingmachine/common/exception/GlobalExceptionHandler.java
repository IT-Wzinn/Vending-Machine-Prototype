package com.vendingmachine.common.exception;

import com.vendingmachine.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        String requestId = getOrCreateRequestId(request);
        log.warn("Resource not found: {} (RequestId: {})", ex.getMessage(), requestId);
        ApiErrorResponse response = ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage(), requestId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        String requestId = getOrCreateRequestId(request);
        log.warn("Path not found: {} (RequestId: {})", ex.getResourcePath(), requestId);
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.NOT_FOUND, "Đường dẫn tài nguyên không tồn tại.", requestId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String requestId = getOrCreateRequestId(request);
        log.warn("HTTP method not supported: {} (RequestId: {})", ex.getMethod(), requestId);
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ.", requestId);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String requestId = getOrCreateRequestId(request);
        log.warn("Business rule violation: {} [{}] (RequestId: {})", ex.getErrorCode(), ex.getMessage(), requestId);
        ApiErrorResponse response = ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage(), requestId);
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = getOrCreateRequestId(request);
        FieldError quantityError = ex.getBindingResult().getFieldError("quantity");
        if (quantityError != null) {
            String message = quantityError.getDefaultMessage() != null ? quantityError.getDefaultMessage() : "Chỉ có thể đặt 1 sản phẩm mỗi lần.";
            log.warn("Validation failed on quantity: {} (RequestId: {})", message, requestId);
            ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.INVALID_QUANTITY, message, requestId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        String message = details.isEmpty() ? "Dữ liệu không hợp lệ. Vui lòng thử lại." : details;
        log.warn("Validation failed: {} (RequestId: {})", message, requestId);
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, message, requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestId = getOrCreateRequestId(request);
        log.error("Unhandled server exception occurred (RequestId: {})", requestId, ex);
        // Do not expose internal stack trace or SQL exception details to customers
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Đã có lỗi hệ thống xảy ra. Vui lòng thử lại sau.",
                requestId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String getOrCreateRequestId(HttpServletRequest request) {
        String headerRequestId = request.getHeader("X-Request-Id");
        if (headerRequestId != null && !headerRequestId.isBlank()) {
            return headerRequestId;
        }
        return UUID.randomUUID().toString();
    }
}
