package com.vibeaiteam.investmentqueryapi.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

@RestControllerAdvice
public class InvestmentQueryExceptionHandler {

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @ExceptionHandler(InvestmentQueryValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(InvestmentQueryValidationException ex) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(InvestmentQueryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(InvestmentQueryNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvestmentQueryForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(InvestmentQueryForbiddenException ex) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
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