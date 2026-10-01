package com.vibeaiteam.profitcalculation.controller.profit.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Profit 계산 API의 404 에러를 { "error": "NOT_FOUND", "message": "..." } 형식으로 변환한다.
 * 기존 모듈의 SubscriptionExceptionHandler 패턴을 따른다.
 */
@RestControllerAdvice
public class ProfitExceptionHandler {

  /**
   * ProfitNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(ProfitNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ProfitNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * ProfitForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(ProfitForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ProfitForbiddenException ex) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    return ErrorResponses.of(status, errorCode, message);
  }
}
