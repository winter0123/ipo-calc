package com.ipocalc.notification.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 알림 입력 검증 실패(HTTP 400). */
public class NotificationValidationException extends ApiException {

  private static final long serialVersionUID = 1L;

  /** 메시지를 담아 예외를 생성한다. */
  public NotificationValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
