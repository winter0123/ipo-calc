package com.vibeaiteam.costrecording.controller.cost.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 비용 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class CostExceptionHandler {

  /**
   * CostValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(CostValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(CostValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /**
   * CostNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(CostNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(CostNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * CostForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(CostForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(CostForbiddenException ex) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    return ErrorResponses.of(status, errorCode, message);
  }
}
