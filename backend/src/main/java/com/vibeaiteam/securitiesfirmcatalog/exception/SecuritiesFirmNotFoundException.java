package com.vibeaiteam.securitiesfirmcatalog.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 증권사 대상 없음 예외(HTTP 404). */
public class SecuritiesFirmNotFoundException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesFirmNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
