package com.vibeaiteam.accountauthentication.exception;

/** 현재 비밀번호 불일치 예외(HTTP 400). */
public class InvalidCurrentPasswordException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvalidCurrentPasswordException(String message) {
    super(message);
  }
}
