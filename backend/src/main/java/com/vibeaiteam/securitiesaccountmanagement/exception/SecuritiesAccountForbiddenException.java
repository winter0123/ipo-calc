package com.vibeaiteam.securitiesaccountmanagement.exception;

/**
 * 증권계좌 소유권 위반 예외(HTTP 403).
 */
public class SecuritiesAccountForbiddenException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public SecuritiesAccountForbiddenException(String message) {
    super(message);
  }
}
