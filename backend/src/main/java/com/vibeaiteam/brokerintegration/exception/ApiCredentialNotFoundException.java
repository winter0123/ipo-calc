package com.vibeaiteam.brokerintegration.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 브로커 API 인증정보 대상 없음 예외(HTTP 404). */
public class ApiCredentialNotFoundException extends ApiException {

  private static final long serialVersionUID = 1L;

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 시크릿이 포함되지 않은 조회 실패 메시지
   */
  public ApiCredentialNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
