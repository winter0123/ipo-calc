package com.vibeaiteam.dashboard.exception;

/** 대시보드 대상 없음 예외(HTTP 404). */
public class DashboardNotFoundException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public DashboardNotFoundException(String message) {
    super(message);
  }
}
