package com.vibeaiteam.accountauthentication.exception;

import org.springframework.http.HttpStatus;

/** 가입이 닫혀 있을 때 던진다. */
public class SignupClosedException extends ApiException {

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 사용자에게 보여줄 메시지
   */
  public SignupClosedException(String message) {
    super(HttpStatus.FORBIDDEN, "SIGNUP_CLOSED", message);
  }
}
