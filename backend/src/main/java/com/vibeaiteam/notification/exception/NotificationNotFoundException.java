package com.vibeaiteam.notification.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 본인 소유의 구독을 찾을 수 없다(HTTP 404). 남의 구독 존재 여부는 알리지 않는다. */
public class NotificationNotFoundException extends ApiException {

  private static final long serialVersionUID = 1L;

  /** 메시지를 담아 예외를 생성한다. */
  public NotificationNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
