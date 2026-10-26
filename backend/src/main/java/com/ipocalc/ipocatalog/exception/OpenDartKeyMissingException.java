package com.ipocalc.ipocatalog.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** OpenDART 키가 설정되지 않았을 때(HTTP 503). */
public class OpenDartKeyMissingException extends ApiException {

  /** 기본 메시지로 예외를 생성한다. */
  public OpenDartKeyMissingException() {
    super(HttpStatus.SERVICE_UNAVAILABLE, "OPENDART_KEY_MISSING", "OpenDART 키가 설정되지 않았습니다.");
  }
}
