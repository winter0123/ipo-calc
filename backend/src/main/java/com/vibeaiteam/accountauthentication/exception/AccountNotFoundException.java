package com.vibeaiteam.accountauthentication.exception;

import org.springframework.http.HttpStatus;

/** 대상 계정을 찾지 못했을 때 던진다. */
public class AccountNotFoundException extends ApiException {

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 사용자에게 보여줄 메시지
   */
  public AccountNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
