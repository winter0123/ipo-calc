package com.ipocalc.brokerintegration.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 브로커 API 인증정보 중복 등록 예외(HTTP 409). */
public class ApiCredentialConflictException extends ApiException {

  private static final long serialVersionUID = 1L;

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 시크릿이 포함되지 않은 충돌 메시지
   */
  public ApiCredentialConflictException(String message) {
    super(HttpStatus.CONFLICT, "CONFLICT", message);
  }
}
