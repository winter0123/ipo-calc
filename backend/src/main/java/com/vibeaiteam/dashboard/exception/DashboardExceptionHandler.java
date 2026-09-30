package com.vibeaiteam.dashboard.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 대시보드 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class DashboardExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * DashboardValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(DashboardValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(DashboardValidationException ex) {
    return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
  }

  /**
   * DashboardNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(DashboardNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(DashboardNotFoundException ex) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * DashboardForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(DashboardForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(DashboardForbiddenException ex) {
    return response(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    ErrorResponse response = ErrorResponse.builder().error(errorCode).message(message).build();
    return ResponseEntity.status(status).contentType(JSON_UTF8).body(response);
  }
}
