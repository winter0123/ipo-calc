package com.ipocalc.notification.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 서버에 VAPID 키가 없어 푸시 기능이 꺼져 있다(HTTP 503). */
public class NotificationDisabledException extends ApiException {

  private static final long serialVersionUID = 1L;

  /** 안내 문구와 함께 생성한다. */
  public NotificationDisabledException() {
    super(
        HttpStatus.SERVICE_UNAVAILABLE, "PUSH_DISABLED", "이 서버는 푸시 알림이 설정되지 않았습니다. 관리자에게 문의해 주세요");
  }
}
