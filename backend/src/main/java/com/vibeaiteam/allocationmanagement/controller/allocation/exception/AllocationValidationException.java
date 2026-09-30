package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

/**
 * 배정 검증 실패 예외(HTTP 400).
 */
public class AllocationValidationException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public AllocationValidationException(String message) {
    super(message);
  }
}
