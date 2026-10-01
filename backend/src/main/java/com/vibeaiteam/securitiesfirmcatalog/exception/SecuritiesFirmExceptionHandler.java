package com.vibeaiteam.securitiesfirmcatalog.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 증권사 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class SecuritiesFirmExceptionHandler {

  /**
   * SecuritiesFirmValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesFirmValidationException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesFirmValidation(
      SecuritiesFirmValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /**
   * SecuritiesFirmDuplicateException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesFirmDuplicateException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesFirmDuplicate(
      SecuritiesFirmDuplicateException ex) {
    return ErrorResponses.of(HttpStatus.CONFLICT, "DUPLICATE_NAME", ex.getMessage());
  }

  /**
   * SecuritiesFirmNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesFirmNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesFirmNotFound(
      SecuritiesFirmNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }
}
