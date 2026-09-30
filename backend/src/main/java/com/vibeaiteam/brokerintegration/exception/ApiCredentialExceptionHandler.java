package com.vibeaiteam.brokerintegration.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 브로커 API 인증정보 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class ApiCredentialExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * ApiCredentialValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(ApiCredentialValidationException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialValidation(
      ApiCredentialValidationException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("VALIDATION_ERROR").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  /**
   * ApiCredentialNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(ApiCredentialNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialNotFound(
      ApiCredentialNotFoundException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("NOT_FOUND").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(JSON_UTF8).body(response);
  }

  /**
   * ApiCredentialForbiddenException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(ApiCredentialForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialForbidden(
      ApiCredentialForbiddenException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("FORBIDDEN").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(JSON_UTF8).body(response);
  }

  /**
   * ApiCredentialConflictException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(ApiCredentialConflictException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialConflict(
      ApiCredentialConflictException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("CONFLICT").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.CONFLICT).contentType(JSON_UTF8).body(response);
  }

  /**
   * BrokerConnectionUnavailableException을 HTTP 503 응답으로 변환한다.
   */
  @ExceptionHandler(BrokerConnectionUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBrokerConnectionUnavailable(
      BrokerConnectionUnavailableException ex) {
    ErrorResponse response =
        ErrorResponse.builder()
            .error("BROKER_CONNECTION_UNAVAILABLE")
            .message(ex.getMessage())
            .build();
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(JSON_UTF8)
        .body(response);
  }
}
