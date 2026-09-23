package com.harshboss.controller;

import com.harshboss.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

/**
 * Global error handler — converts thrown exceptions into a clean JSON envelope
 * {@code { "error": "...", "message": "...", "status": ..., "timestamp": "..." }}
 * with the correct HTTP status. Every controller relies on this.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException e) {
        log.debug("404: {}", e.getMessage());
        return ErrorResponse.of(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        log.debug("400: {}", e.getMessage());
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        log.debug("400 (validation): {}", msg);
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "validation_error", msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        log.debug("400 (body parse): {}", e.getMessage());
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "bad_request",
                "Request body is missing or malformed JSON.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        log.debug("400 (type mismatch): {}", e.getMessage());
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "bad_request",
                "Path/query parameter '" + e.getName() + "' has an invalid value.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception e) {
        log.error("Unhandled exception", e);
        return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
