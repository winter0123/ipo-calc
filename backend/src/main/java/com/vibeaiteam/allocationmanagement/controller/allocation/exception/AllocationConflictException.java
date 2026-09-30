package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

/**
 * 배정 충돌 예외(HTTP 409).
 */
public class AllocationConflictException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public AllocationConflictException(String message) {
    super(message);
  }
}
