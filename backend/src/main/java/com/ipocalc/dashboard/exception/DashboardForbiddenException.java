package com.ipocalc.dashboard.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 대시보드 소유권 위반 예외(HTTP 403). */
public class DashboardForbiddenException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public DashboardForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
