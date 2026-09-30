package com.vibeaiteam.securitiesfirmcatalog.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 증권사 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class SecuritiesFirmExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * SecuritiesFirmValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesFirmValidationException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesFirmValidation(
      SecuritiesFirmValidationException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("VALIDATION_ERROR").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  /**
   * SecuritiesFirmDuplicateException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesFirmDuplicateException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesFirmDuplicate(
      SecuritiesFirmDuplicateException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("DUPLICATE_NAME").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.CONFLICT).contentType(JSON_UTF8).body(response);
  }

  /**
   * SecuritiesFirmNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(SecuritiesFirmNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleSecuritiesFirmNotFound(
      SecuritiesFirmNotFoundException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("NOT_FOUND").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(JSON_UTF8).body(response);
  }
}
