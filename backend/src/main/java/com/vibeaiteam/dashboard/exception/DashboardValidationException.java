package com.vibeaiteam.dashboard.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 대시보드 검증 실패 예외(HTTP 400). */
public class DashboardValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public DashboardValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
  }
}
