package com.vibeaiteam.ipocatalog.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** CSV 형식 오류 예외(HTTP 400). */
public class IpoInvalidCsvException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public IpoInvalidCsvException(String message) {
    super(HttpStatus.BAD_REQUEST, "INVALID_CSV", message);
  }
}
