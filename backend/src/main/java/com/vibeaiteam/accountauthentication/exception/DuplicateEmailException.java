package com.vibeaiteam.accountauthentication.exception;

import org.springframework.http.HttpStatus;

/** 이메일 중복 예외(HTTP 409). */
public class DuplicateEmailException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public DuplicateEmailException(String message) {
    super(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", message);
  }
}
