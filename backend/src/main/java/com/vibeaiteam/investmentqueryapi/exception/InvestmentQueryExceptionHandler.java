package com.vibeaiteam.investmentqueryapi.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 투자 이력 조회 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class InvestmentQueryExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * InvestmentQueryValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(InvestmentQueryValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(InvestmentQueryValidationException ex) {
    return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
  }

  /**
   * InvestmentQueryNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(InvestmentQueryNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(InvestmentQueryNotFoundException ex) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * InvestmentQueryForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(InvestmentQueryForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(InvestmentQueryForbiddenException ex) {
    return response(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    ErrorResponse response = ErrorResponse.builder().error(errorCode).message(message).build();
    return ResponseEntity.status(status).contentType(JSON_UTF8).body(response);
  }
}
