package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 배정 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class AllocationExceptionHandler {

  /**
   * AllocationValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(AllocationValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(AllocationValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /**
   * AllocationConflictException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(AllocationConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(AllocationConflictException ex) {
    return ErrorResponses.of(HttpStatus.CONFLICT, "DUPLICATE_ALLOCATION", ex.getMessage());
  }

  /**
   * AllocationNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(AllocationNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(AllocationNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * AllocationForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(AllocationForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(AllocationForbiddenException ex) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    return ErrorResponses.of(status, errorCode, message);
  }
}
