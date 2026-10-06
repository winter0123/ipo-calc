package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

/** OpenDART 인증(API 키) 오류 예외. */
public class OpenDartAuthenticationException extends OpenDartApiException {

  /** OpenDART 상태 코드와 메시지로 예외를 생성한다. */
  public OpenDartAuthenticationException(String statusCode, String message) {
    super(statusCode, message);
  }
}
