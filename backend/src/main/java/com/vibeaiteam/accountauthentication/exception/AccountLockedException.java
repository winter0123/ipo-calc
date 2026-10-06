package com.vibeaiteam.accountauthentication.exception;

/** 계정 잠김 예외(HTTP 403). */
public class AccountLockedException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public AccountLockedException(String message) {
    super(message);
  }
}
