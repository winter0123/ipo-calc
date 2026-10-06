package com.vibeaiteam.securitiesaccountmanagement.exception;

/** 증권계좌 검증 실패 예외(HTTP 400). */
public class SecuritiesAccountValidationException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesAccountValidationException(String message) {
    super(message);
  }
}
