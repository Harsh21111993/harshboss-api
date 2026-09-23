package com.harshboss.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

/**
 * Standard JSON error envelope returned by {@code GlobalExceptionHandler}.
 * Shape: {@code { "error": "...", "message": "...", "timestamp": "...", "status": 4xx }}.
 */
public record ErrorResponse(
        String error,
        String message,
        int status,
        Instant timestamp
) {
    public static ResponseEntity<ErrorResponse> of(HttpStatus httpStatus, String error, String message) {
        ErrorResponse body = new ErrorResponse(error, message, httpStatus.value(), Instant.now());
        return ResponseEntity.status(httpStatus).body(body);
    }

    public Map<String, Object> asMap() {
        return Map.of(
                "error", error,
                "message", message,
                "status", status,
                "timestamp", timestamp.toString()
        );
    }
}
