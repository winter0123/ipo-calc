package com.ipocalc.accountauthentication.controller.dto;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 예외 핸들러가 공통으로 쓰는 에러 응답 생성기.
 *
 * <p>모든 핸들러가 같은 모양(상태 코드 + {@code application/json;charset=UTF-8} + {@link ErrorResponse})으로 응답하도록
 * 한 곳에서 만든다.
 */
public final class ErrorResponses {

  /** 에러 응답의 Content-Type. */
  public static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  private ErrorResponses() {}

  /**
   * 에러 코드와 메시지를 담은 응답을 만든다.
   *
   * @param status HTTP 상태
   * @param errorCode 에러 코드(예: NOT_FOUND)
   * @param message 사용자에게 보여줄 메시지
   * @return 에러 응답
   */
  public static ResponseEntity<ErrorResponse> of(
      HttpStatus status, String errorCode, String message) {
    ErrorResponse body = ErrorResponse.builder().error(errorCode).message(message).build();
    return ResponseEntity.status(status).contentType(JSON_UTF8).body(body);
  }

  /**
   * 필드별 검증 오류를 담은 400 응답을 만든다(에러 코드 VALIDATION_FAILED).
   *
   * @param fieldErrors 필드별 오류 목록
   * @return 400 응답
   */
  public static ResponseEntity<ErrorResponse> validationFailed(
      List<ErrorResponse.FieldError> fieldErrors) {
    ErrorResponse body =
        ErrorResponse.builder()
            .error("VALIDATION_FAILED")
            .message("입력값 검증에 실패했습니다")
            .fieldErrors(fieldErrors)
            .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(body);
  }
}
