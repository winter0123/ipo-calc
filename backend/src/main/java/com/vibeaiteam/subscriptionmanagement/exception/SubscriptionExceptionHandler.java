package com.vibeaiteam.subscriptionmanagement.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 청약 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class SubscriptionExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * SubscriptionValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(SubscriptionValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(SubscriptionValidationException ex) {
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /**
   * SubscriptionConflictException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(SubscriptionConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(SubscriptionConflictException ex) {
    return response(HttpStatus.CONFLICT, "DUPLICATE_SUBSCRIPTION", ex.getMessage());
  }

  /**
   * SubscriptionNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(SubscriptionNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(SubscriptionNotFoundException ex) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * SubscriptionForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(SubscriptionForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(SubscriptionForbiddenException ex) {
    return response(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    ErrorResponse response = ErrorResponse.builder().error(errorCode).message(message).build();
    return ResponseEntity.status(status).contentType(JSON_UTF8).body(response);
  }
}
