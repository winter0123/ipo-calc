package com.vibeaiteam.dashboard.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 대시보드 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class DashboardExceptionHandler {

  /**
   * DashboardValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(DashboardValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(DashboardValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
  }

  /**
   * DashboardNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(DashboardNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(DashboardNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * DashboardForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(DashboardForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(DashboardForbiddenException ex) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    return ErrorResponses.of(status, errorCode, message);
  }
}
