package com.vibeaiteam.brokerintegration.exception;

/**
 * 브로커 API 인증정보 입력 검증 실패 예외(HTTP 400).
 */
public class ApiCredentialValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 시크릿이 포함되지 않은 검증 실패 메시지
   */
  public ApiCredentialValidationException(String message) {
    super(message);
  }
}
