package com.vibeaiteam.ipocatalog.exception;

/** 공모주 대상 없음 예외(HTTP 404). */
public class IpoNotFoundException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public IpoNotFoundException(String message) {
    super(message);
  }
}
