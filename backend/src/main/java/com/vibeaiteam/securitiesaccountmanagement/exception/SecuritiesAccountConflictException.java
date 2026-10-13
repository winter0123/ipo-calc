package com.vibeaiteam.securitiesaccountmanagement.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 증권계좌 충돌 예외(HTTP 409). */
public class SecuritiesAccountConflictException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesAccountConflictException(String message) {
    super(HttpStatus.CONFLICT, "CONFLICT", message);
  }
}
