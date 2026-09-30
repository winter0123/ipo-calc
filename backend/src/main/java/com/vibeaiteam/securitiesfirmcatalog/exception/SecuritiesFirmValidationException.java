package com.vibeaiteam.securitiesfirmcatalog.exception;

/**
 * 증권사 검증 실패 예외(HTTP 400).
 */
public class SecuritiesFirmValidationException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public SecuritiesFirmValidationException(String message) {
    super(message);
  }
}
