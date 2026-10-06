package com.vibeaiteam.securitiesaccountmanagement.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 증권계좌 예외를 HTTP 에러 응답으로 변환한다. */
@RestControllerAdvice
public class SecuritiesAccountExceptionHandler {

  /** SecuritiesAccountValidationException을 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(SecuritiesAccountValidationException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountValidation(
      SecuritiesAccountValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /** SecuritiesAccountNotFoundException을 HTTP 404 응답으로 변환한다. */
  @ExceptionHandler(SecuritiesAccountNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountNotFound(
      SecuritiesAccountNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /** SecuritiesAccountForbiddenException을 HTTP 403 응답으로 변환한다. */
  @ExceptionHandler(SecuritiesAccountForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountForbidden(
      SecuritiesAccountForbiddenException ex) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  /** SecuritiesAccountConflictException을 HTTP 409 응답으로 변환한다. */
  @ExceptionHandler(SecuritiesAccountConflictException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountConflict(
      SecuritiesAccountConflictException ex) {
    return ErrorResponses.of(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
  }
}
