package com.vibeaiteam.ipocatalog.exception;

/**
 * 공모주 검증 실패 예외(HTTP 400).
 */
public class IpoValidationException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public IpoValidationException(String message) {
    super(message);
  }
}
