package com.vibeaiteam.dashboard.exception;

/**
 * 대시보드 검증 실패 예외(HTTP 400).
 */
public class DashboardValidationException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public DashboardValidationException(String message) {
    super(message);
  }
}
