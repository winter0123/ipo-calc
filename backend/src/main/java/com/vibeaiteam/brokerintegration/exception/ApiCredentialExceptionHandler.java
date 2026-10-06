package com.vibeaiteam.brokerintegration.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 브로커 API 인증정보 예외를 HTTP 에러 응답으로 변환한다. */
@RestControllerAdvice
public class ApiCredentialExceptionHandler {

  /** ApiCredentialValidationException을 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(ApiCredentialValidationException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialValidation(
      ApiCredentialValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /** ApiCredentialNotFoundException을 HTTP 404 응답으로 변환한다. */
  @ExceptionHandler(ApiCredentialNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialNotFound(
      ApiCredentialNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /** ApiCredentialForbiddenException을 HTTP 403 응답으로 변환한다. */
  @ExceptionHandler(ApiCredentialForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialForbidden(
      ApiCredentialForbiddenException ex) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  /** ApiCredentialConflictException을 HTTP 409 응답으로 변환한다. */
  @ExceptionHandler(ApiCredentialConflictException.class)
  public ResponseEntity<ErrorResponse> handleApiCredentialConflict(
      ApiCredentialConflictException ex) {
    return ErrorResponses.of(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
  }

  /** BrokerConnectionUnavailableException을 HTTP 503 응답으로 변환한다. */
  @ExceptionHandler(BrokerConnectionUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBrokerConnectionUnavailable(
      BrokerConnectionUnavailableException ex) {
    return ErrorResponses.of(
        HttpStatus.SERVICE_UNAVAILABLE, "BROKER_CONNECTION_UNAVAILABLE", ex.getMessage());
  }

  /** RateLimitExceededException을 HTTP 429 응답으로 변환한다. {@code Retry-After} 헤더에 남은 초를 담는다. */
  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitExceededException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("RATE_LIMITED").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .contentType(ErrorResponses.JSON_UTF8)
        .body(response);
  }

  /**
   * BrokerApiException을 HTTP 응답으로 변환한다. 인증 거부는 424(키 갱신 안내), 분당 제한은 429, 그 밖은 502다. 증권사 응답 원문·URL은
   * 노출하지 않는다.
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
      message = "증권사 요청 제한에 걸렸습니다. 잠시 후 다시 시도해 주세요.";
    }
    return ErrorResponses.of(status, error, message);
  }
}
