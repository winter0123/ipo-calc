package com.vibeaiteam.securitiesfirmcatalog.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 증권사 검증 실패 예외(HTTP 400). */
public class SecuritiesFirmValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesFirmValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
