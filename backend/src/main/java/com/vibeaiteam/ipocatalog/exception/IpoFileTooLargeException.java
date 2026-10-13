package com.vibeaiteam.ipocatalog.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** CSV 파일 크기 초과 예외(HTTP 400). */
public class IpoFileTooLargeException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public IpoFileTooLargeException(String message) {
    super(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", message);
  }
}
