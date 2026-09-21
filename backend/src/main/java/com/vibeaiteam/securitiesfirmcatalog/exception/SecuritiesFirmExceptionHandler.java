package com.vibeaiteam.securitiesfirmcatalog.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

@RestControllerAdvice
public class SecuritiesFirmExceptionHandler {

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @ExceptionHandler(SecuritiesFirmValidationException.class)
    public ResponseEntity<ErrorResponse> handleSecuritiesFirmValidation(SecuritiesFirmValidationException ex) {
        ErrorResponse response = ErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(JSON_UTF8)
                .body(response);
    }

    @ExceptionHandler(SecuritiesFirmDuplicateException.class)
    public ResponseEntity<ErrorResponse> handleSecuritiesFirmDuplicate(SecuritiesFirmDuplicateException ex) {
        ErrorResponse response = ErrorResponse.builder()
                .error("DUPLICATE_NAME")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(JSON_UTF8)
                .body(response);
    }
}