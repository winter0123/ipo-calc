package com.ipocalc.securitiesaccountmanagement.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 증권계좌 검증 실패 예외(HTTP 400). */
public class SecuritiesAccountValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesAccountValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
