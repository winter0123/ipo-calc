package com.ipocalc.accountauthentication.exception;

import org.springframework.http.HttpStatus;

/** 하위 기록이 있어 취소할 수 없을 때의 예외(HTTP 409). */
public class HasChildRecordsException extends ApiException {

  private static final long serialVersionUID = 1L;

  /** 메시지를 담아 예외를 생성한다. */
  public HasChildRecordsException(String message) {
    super(HttpStatus.CONFLICT, "HAS_CHILD_RECORDS", message);
  }
}
