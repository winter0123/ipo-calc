package com.vibeaiteam.ipocatalog.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 공모주 충돌 예외(HTTP 409). */
public class IpoConflictException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public IpoConflictException(String message) {
    super(HttpStatus.CONFLICT, "DUPLICATE_IPO", message);
  }
}
