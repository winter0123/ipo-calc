package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 배정 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class AllocationExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * AllocationValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(AllocationValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(AllocationValidationException ex) {
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /**
   * AllocationConflictException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(AllocationConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(AllocationConflictException ex) {
    return response(HttpStatus.CONFLICT, "DUPLICATE_ALLOCATION", ex.getMessage());
  }

  /**
   * AllocationNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(AllocationNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(AllocationNotFoundException ex) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    ErrorResponse response = ErrorResponse.builder().error(errorCode).message(message).build();
    return ResponseEntity.status(status).contentType(JSON_UTF8).body(response);
  }
}
