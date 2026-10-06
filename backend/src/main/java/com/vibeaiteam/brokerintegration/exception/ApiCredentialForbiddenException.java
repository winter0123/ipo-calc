package com.vibeaiteam.brokerintegration.exception;

/** 브로커 API 인증정보 소유권 위반 예외(HTTP 403). */
public class ApiCredentialForbiddenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 시크릿이 포함되지 않은 권한 없음 메시지
   */
  public ApiCredentialForbiddenException(String message) {
    super(message);
  }
}
