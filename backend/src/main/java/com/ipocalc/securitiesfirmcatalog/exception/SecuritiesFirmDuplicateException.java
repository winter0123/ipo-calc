package com.ipocalc.securitiesfirmcatalog.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 증권사 중복 예외(HTTP 409). */
public class SecuritiesFirmDuplicateException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SecuritiesFirmDuplicateException(String message) {
    super(HttpStatus.CONFLICT, "DUPLICATE_NAME", message);
  }
}
