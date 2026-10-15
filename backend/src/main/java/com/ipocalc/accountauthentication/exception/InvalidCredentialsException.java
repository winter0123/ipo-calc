package com.ipocalc.accountauthentication.exception;

import org.springframework.http.HttpStatus;

/** 인증 정보 불일치 예외(HTTP 401). */
public class InvalidCredentialsException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvalidCredentialsException(String message) {
    super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
  }
}
