package com.vibeaiteam.dashboard.exception;

/**
 * 대시보드 소유권 위반 예외(HTTP 403).
 */
public class DashboardForbiddenException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public DashboardForbiddenException(String message) {
    super(message);
  }
}
