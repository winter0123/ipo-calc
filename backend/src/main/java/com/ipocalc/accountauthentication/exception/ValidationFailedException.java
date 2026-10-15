package com.ipocalc.accountauthentication.exception;

/** 필드 단위 검증 실패 예외(HTTP 400). */
public class ValidationFailedException extends RuntimeException {

  private final String field;
  private final String message;

  /** 검증에 실패한 필드명과 메시지로 예외를 생성한다. */
  public ValidationFailedException(String field, String message) {
    super(message);
    this.field = field;
    this.message = message;
  }

  public String getField() {
    return field;
  }

  public String getMessage() {
    return message;
  }
}
