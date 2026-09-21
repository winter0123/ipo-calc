package com.vibeaiteam.ipocatalog.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;

@RestControllerAdvice
public class IpoExceptionHandler {

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @ExceptionHandler(IpoValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IpoValidationException ex) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(IpoInvalidCsvException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCsv(IpoInvalidCsvException ex) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_CSV", ex.getMessage());
    }

    @ExceptionHandler(IpoFileTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(IpoFileTooLargeException ex) {
        return response(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return response(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "CSV 파일은 최대 5MB까지 업로드할 수 있습니다.");
    }

    @ExceptionHandler(IpoConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IpoConflictException ex) {
        return response(HttpStatus.CONFLICT, "DUPLICATE_IPO", ex.getMessage());
    }

    @ExceptionHandler(IpoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IpoNotFoundException ex) {
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
