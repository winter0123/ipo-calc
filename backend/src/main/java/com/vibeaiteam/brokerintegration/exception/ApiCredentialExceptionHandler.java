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

  /**
   * BrokerApiException을 HTTP 응답으로 변환한다. 인증 거부는 424(키 갱신 안내), 분당 제한은 429, 그 밖은 502다. 증권사 응답
   * 원문·URL은 노출하지 않는다.
   */
  @ExceptionHandler(BrokerApiException.class)
  public ResponseEntity<ErrorResponse> handleBrokerApi(BrokerApiException ex) {
    String code = String.valueOf(ex.getStatusCode());
    HttpStatus status = HttpStatus.BAD_GATEWAY;
    String error = "BROKER_API_ERROR";
    String message = "증권사 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.";
    if ("auth_failed".equals(code)) {
      status = HttpStatus.FAILED_DEPENDENCY;
      error = "BROKER_AUTH_FAILED";
      message = "증권사 인증에 실패했습니다. 키가 만료됐거나 잘못됐을 수 있으니 키를 갱신해 주세요.";
    } else if ("rate_limited".equals(code)) {
      status = HttpStatus.TOO_MANY_REQUESTS;
      error = "BROKER_RATE_LIMITED";
      message = "증권사 요청 제한에 걸렸습니다. 1분 뒤 다시 시도해 주세요.";
    }
    ErrorResponse response = ErrorResponse.builder().error(error).message(message).build();
    return ResponseEntity.status(status).contentType(JSON_UTF8).body(response);
  }
}
