package com.vibeaiteam.ipocatalog.exception;

/**
 * 공모주 충돌 예외(HTTP 409).
 */
public class IpoConflictException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public IpoConflictException(String message) {
    super(message);
  }
}
