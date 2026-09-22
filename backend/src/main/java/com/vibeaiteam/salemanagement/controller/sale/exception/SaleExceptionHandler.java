package com.vibeaiteam.salemanagement.controller.sale.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

@RestControllerAdvice
public class SaleExceptionHandler {

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @ExceptionHandler(SaleValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(SaleValidationException ex) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(SaleAllocationExceededException.class)
    public ResponseEntity<ErrorResponse> handleAllocationExceeded(SaleAllocationExceededException ex) {
        return response(HttpStatus.BAD_REQUEST, "ALLOCATION_EXCEEDED", ex.getMessage());
    }

    @ExceptionHandler(SaleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SaleNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String errorCode, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .error(errorCode)
                .message(message)
                .build();
        return ResponseEntity.status(status)
                .contentType(JSON_UTF8)
                .body(response);
    }
}