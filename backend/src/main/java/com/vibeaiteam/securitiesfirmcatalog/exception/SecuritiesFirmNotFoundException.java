package com.vibeaiteam.securitiesfirmcatalog.exception;

/** 증권사 대상 없음 예외(HTTP 404). */
public class SecuritiesFirmNotFoundException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesFirmNotFoundException(String message) {
    super(message);
  }
}
