package com.vibeaiteam.ipocatalog.exception;

/**
 * CSV 형식 오류 예외(HTTP 400).
 */
public class IpoInvalidCsvException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public IpoInvalidCsvException(String message) {
    super(message);
  }
}
