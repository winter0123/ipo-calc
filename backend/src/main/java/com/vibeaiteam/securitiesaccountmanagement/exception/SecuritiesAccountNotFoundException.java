package com.vibeaiteam.securitiesaccountmanagement.exception;

/**
 * 증권계좌 대상 없음 예외(HTTP 404).
 */
public class SecuritiesAccountNotFoundException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public SecuritiesAccountNotFoundException(String message) {
    super(message);
  }
}
