package com.vibeaiteam.investormanagement.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 투자자 예외를 HTTP 에러 응답으로 변환한다. */
@RestControllerAdvice
public class InvestorExceptionHandler {

  /** InvestorValidationException을 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(InvestorValidationException.class)
  public ResponseEntity<ErrorResponse> handleInvestorValidation(InvestorValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /** InvestorNotFoundException을 HTTP 404 응답으로 변환한다. */
  @ExceptionHandler(InvestorNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleInvestorNotFound(InvestorNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /** InvestorForbiddenException을 HTTP 403 응답으로 변환한다. */
  @ExceptionHandler(InvestorForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleInvestorForbidden(InvestorForbiddenException ex) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }
}
