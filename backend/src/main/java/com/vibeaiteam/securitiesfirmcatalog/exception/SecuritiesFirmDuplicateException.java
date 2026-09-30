package com.vibeaiteam.securitiesfirmcatalog.exception;

/**
 * 증권사 중복 예외(HTTP 409).
 */
public class SecuritiesFirmDuplicateException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public SecuritiesFirmDuplicateException(String message) {
    super(message);
  }
}
