package com.vibeaiteam.securitiesaccountmanagement.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 증권계좌 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class SecuritiesAccountExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * SecuritiesAccountValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesAccountValidationException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountValidation(
      SecuritiesAccountValidationException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("VALIDATION_ERROR").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  /**
   * SecuritiesAccountNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesAccountNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountNotFound(
      SecuritiesAccountNotFoundException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("NOT_FOUND").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(JSON_UTF8).body(response);
  }

  /**
   * SecuritiesAccountForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesAccountForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountForbidden(
      SecuritiesAccountForbiddenException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("FORBIDDEN").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(JSON_UTF8).body(response);
  }

  /**
   * SecuritiesAccountConflictException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesAccountConflictException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesAccountConflict(
      SecuritiesAccountConflictException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("CONFLICT").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.CONFLICT).contentType(JSON_UTF8).body(response);
  }
}
