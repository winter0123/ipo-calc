package com.vibeaiteam.accountauthentication.exception;

/** 이메일 중복 예외(HTTP 409). */
public class DuplicateEmailException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public DuplicateEmailException(String message) {
    super(message);
  }
}
