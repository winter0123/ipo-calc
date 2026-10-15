package com.ipocalc.securitiesaccountmanagement.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 증권계좌 소유권 위반 예외(HTTP 403). */
public class SecuritiesAccountForbiddenException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesAccountForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
